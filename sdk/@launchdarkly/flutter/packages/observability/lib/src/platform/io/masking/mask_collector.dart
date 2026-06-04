import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

import 'mask_operation.dart';
import 'masking_policy.dart';

/// Walks the element tree under the capture boundary once and produces the
/// resolved list of rectangles to redact, in the boundary's *logical*
/// coordinate space.
///
/// This is the Flutter counterpart of the native `MaskCollector`. Like the
/// native collector it resolves masking precedence *during* the walk — every
/// emitted [MaskOperation] is a region that must be filled, so the downstream
/// [MaskStabilizer] / [MaskApplier] never need to reason about reveal/unmask
/// semantics:
///
/// - An [MaskingPolicy.isExplicitMask] subtree emits one op for its bounds and
///   the walk stops descending (the whole subtree is covered, so a nested
///   `LDUnmask` correctly stays masked).
/// - An [MaskingPolicy.isUnmask] subtree is descended with `underUnmask` set so
///   globally-masked leaves inside it are skipped — but an explicit mask nested
///   deeper still emits, preserving "explicit mask wins".
/// - A [MaskingPolicy.isGloballyMasked] leaf emits unless it is under an unmask.
///
/// The walk order is deterministic, so two passes over an unchanged tree yield
/// positionally-aligned lists that [MaskStabilizer] can zip together.
///
/// Z-order / occlusion: children are visited in paint order — Flutter paints
/// siblings in child order, so a later sibling (and its subtree) is drawn *on
/// top of* earlier ones, the same role `zPosition` plays in the native
/// collector. When an opaque widget is reached it fully hides whatever was
/// painted beneath it, so any mask already collected for a region it covers is
/// redundant and is dropped (mirroring the native "opaque container absorbs its
/// children's masks" pass). The opacity test is deliberately biased toward
/// transparency: a mask is only removed when we are certain the covering widget
/// paints a solid, fully-opaque rectangle over it, so we never drop a mask that
/// might still be visible.
class MaskCollector {
  const MaskCollector(this.policy);

  final MaskingPolicy policy;

  /// Collects redaction rectangles under [boundary], walking from [rootContext].
  List<MaskOperation> collect(
    BuildContext rootContext,
    RenderRepaintBoundary boundary,
  ) {
    final operations = <MaskOperation>[];

    ui.Rect? rectFor(Element element) {
      final renderObject = element.findRenderObject();
      if (renderObject is! RenderBox || !renderObject.attached) {
        return null;
      }
      final topLeft = renderObject.localToGlobal(
        ui.Offset.zero,
        ancestor: boundary,
      );
      return ui.Rect.fromLTWH(
        topLeft.dx,
        topLeft.dy,
        renderObject.size.width,
        renderObject.size.height,
      );
    }

    void visit(Element element, bool underUnmask) {
      final widget = element.widget;

      if (policy.isExplicitMask(widget)) {
        final rect = rectFor(element);
        if (rect != null) {
          operations.add(MaskOperation(rect: rect));
        }
        // Whole subtree is covered by this mask; stop descending so a nested
        // LDUnmask can't carve a hole and we don't emit redundant child masks.
        return;
      }

      if (policy.isUnmask(widget)) {
        element.visitChildren((child) => visit(child, true));
        return;
      }

      if (!underUnmask && policy.isGloballyMasked(widget)) {
        final rect = rectFor(element);
        if (rect != null) {
          operations.add(MaskOperation(rect: rect));
        }
      } else if (operations.isNotEmpty && _isOpaqueCover(widget)) {
        // This widget is about to paint a solid fill over everything visited
        // (i.e. painted) before it. Drop masks it fully covers — the sensitive
        // content behind it isn't visible in the frame, so masking it is
        // redundant. Runs before descending so the widget's own children, which
        // paint on top of its fill, are unaffected.
        final rect = rectFor(element);
        if (rect != null) {
          operations.removeWhere((op) => _covers(rect, op.rect));
        }
      }

      element.visitChildren((child) => visit(child, underUnmask));
    }

    rootContext.visitChildElements((child) => visit(child, false));
    return operations;
  }

  /// `true` only when [widget] is known to paint a fully-opaque rectangle over
  /// its whole bounds. Biased toward `false` (transparency) for safety: an
  /// unrecognized or partially-transparent widget never removes a mask.
  static bool _isOpaqueCover(Widget widget) {
    if (widget is ColoredBox) {
      return widget.color.a >= 1.0;
    }
    if (widget is DecoratedBox &&
        widget.position == DecorationPosition.background) {
      final decoration = widget.decoration;
      if (decoration is BoxDecoration) {
        final color = decoration.color;
        return color != null &&
            color.a >= 1.0 &&
            decoration.shape == BoxShape.rectangle &&
            decoration.borderRadius == null &&
            decoration.gradient == null &&
            decoration.image == null &&
            decoration.backgroundBlendMode == null;
      }
    }
    return false;
  }

  /// `true` if [cover] fully contains [inner] (strict, no slack) so [inner] is
  /// entirely hidden behind the covering widget.
  static bool _covers(ui.Rect cover, ui.Rect inner) =>
      inner.left >= cover.left &&
      inner.top >= cover.top &&
      inner.right <= cover.right &&
      inner.bottom <= cover.bottom;
}
