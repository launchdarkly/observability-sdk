import 'package:flutter/widgets.dart';

import '../../../masking.dart';

/// Pure rule engine that decides, per widget, how it participates in masking.
///
/// This is the Flutter counterpart of the native `MaskingPolicy`. It does not
/// walk the tree (that is [MaskCollector]'s job); it only answers per-element
/// questions based on the screen-wide `PrivacyOptions` and the per-widget
/// [LDMask] / [LDUnmask] markers.
///
/// Precedence (highest first):
/// 1. [LDMask] — explicitly redacts its whole subtree. Always wins.
/// 2. [LDUnmask] — suppresses *global* masking within its subtree, but never
///    overrides an enclosing [LDMask].
/// 3. Global config — the `maskTextInputs` / `maskLabels` / `maskImages` /
///    `maskWebViews` privacy flags, matched against the widget that renders the
///    corresponding content.
class MaskingPolicy {
  const MaskingPolicy({
    required this.maskTextInputs,
    this.maskLabels = false,
    this.maskImages = false,
    this.maskWebViews = false,
  });

  /// Redacts editable text fields (`EditableText`, e.g. `TextField`).
  final bool maskTextInputs;

  /// Redacts static text labels (`RichText`, which backs `Text`/`Text.rich`).
  final bool maskLabels;

  /// Redacts raster images (`RawImage`, which backs `Image`/`Image.*`).
  final bool maskImages;

  /// Redacts embedded platform views (where web views are hosted).
  final bool maskWebViews;

  /// Starts an explicit, type-agnostic mask covering the widget's subtree.
  bool isExplicitMask(Widget widget) => widget is LDMask;

  /// Starts a region that is exempt from global masking.
  bool isUnmask(Widget widget) => widget is LDUnmask;

  /// Whether [widget] is redacted purely by the global privacy config (i.e.
  /// when no explicit marker applies).
  bool isGloballyMasked(Widget widget) {
    if (maskTextInputs && widget is EditableText) {
      return true;
    }
    if (maskLabels && widget is RichText) {
      return true;
    }
    if (maskImages && widget is RawImage) {
      return true;
    }
    if (maskWebViews && _isPlatformView(widget)) {
      return true;
    }
    return false;
  }

  /// Web views (and other embedded native content) are hosted by Flutter via
  /// platform-view widgets. Their pixels are composited outside the Flutter
  /// raster on iOS (and on Android in some embedding modes), so masking the
  /// region is best-effort — we redact the host's bounds so the area is covered
  /// wherever the platform view is composited into the captured frame.
  static bool _isPlatformView(Widget widget) =>
      widget is PlatformViewLink ||
      widget is UiKitView ||
      widget is AndroidView ||
      widget is AppKitView ||
      widget is HtmlElementView;
}
