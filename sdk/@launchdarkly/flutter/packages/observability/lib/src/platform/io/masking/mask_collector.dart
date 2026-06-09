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

  /// Collects redaction regions under [boundary], walking from [rootContext].
  List<MaskOperation> collect(
    BuildContext rootContext,
    RenderRepaintBoundary boundary,
  ) {
    final operations = <MaskOperation>[];

    // Builds the affine mask for [element]: its local bounds plus the transform
    // mapping those bounds into the boundary's logical coordinate space. Using
    // the live transform (rather than an upright bounding box) means a widget
    // that is mid-animation — rotating, scaling, sliding — is captured as the
    // parallelogram it actually occupies this frame, the Flutter analogue of
    // reading a CALayer's `presentation()` on iOS.
    MaskOperation? maskFor(Element element) {
      final renderObject = element.findRenderObject();
      if (renderObject is! RenderBox || !renderObject.attached) {
        return null;
      }
      final size = renderObject.size;
      if (size.width <= 0 || size.height <= 0) {
        return null;
      }
      return MaskOperation(
        localRect: ui.Offset.zero & size,
        transform: renderObject.getTransformTo(boundary),
      );
    }

    void visit(Element element, bool underUnmask) {
      final widget = element.widget;

      if (_isNotPainted(widget, policy.minimumAlpha)) {
        // This subtree isn't painted into the frame (offstage, fully
        // transparent, or hidden), so none of it appears in the captured image.
        // Skip it entirely: there's nothing to redact, and not descending prunes
        // the walk (e.g. the inactive pages of an IndexedStack, or overlay
        // entries hidden beneath an opaque route). Mirrors the native collectors
        // skipping hidden / zero-alpha layers and Android's `!view.isShown`.
        return;
      }

      if (policy.isExplicitMask(widget)) {
        final mask = maskFor(element);
        if (mask != null) {
          operations.add(mask);
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
        final mask = maskFor(element);
        if (mask != null) {
          operations.add(mask);
        }
      } else if (operations.isNotEmpty && _isOpaqueCover(widget)) {
        // This widget is about to paint a solid fill over everything visited
        // (i.e. painted) before it. Drop masks it fully covers — the sensitive
        // content behind it isn't visible in the frame, so masking it is
        // redundant. Runs before descending so the widget's own children, which
        // paint on top of its fill, are unaffected.
        final mask = maskFor(element);
        if (mask != null) {
          operations.removeWhere(
            (op) => _covers(mask.effectiveFrame, op.effectiveFrame),
          );
        }
      }

      element.visitChildren((child) => visit(child, underUnmask));
    }

    rootContext.visitChildElements((child) => visit(child, false));
    return operations;
  }

  /// `true` when [widget] paints nothing into the frame, so its whole subtree
  /// can be skipped: an [Offstage] widget, a near-transparent [Opacity] (at or
  /// below [minimumAlpha]), or an invisible [Visibility]. Conservative — only
  /// well-known "renders nothing" widgets qualify, so a mask is never dropped
  /// for content that is actually visible. The [Opacity] threshold mirrors the
  /// native `minimumAlpha` prune (iOS skips layers with `opacity < minimumAlpha`).
  static bool _isNotPainted(Widget widget, double minimumAlpha) {
    if (widget is Offstage) return widget.offstage;
    if (widget is Opacity) {
      return widget.opacity <= 0.0 || widget.opacity < minimumAlpha;
    }
    if (widget is Visibility) return !widget.visible;
    return false;
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
