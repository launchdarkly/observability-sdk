import 'package:flutter/widgets.dart';

import '../../../masking.dart';
import 'widget_masking_config.dart';

/// Pure rule engine that decides, per widget, how it participates in masking.
///
/// This is the Flutter counterpart of the native `MaskingPolicy`. It does not
/// walk the tree (that is [MaskCollector]'s job); it only answers per-element
/// questions based on the screen-wide `PrivacyOptions` and the per-widget
/// [LDMask] / [LDUnmask] markers.
///
/// Precedence (highest first):
/// 1. [LDMask] / [LDIgnore] (or a config-based mask/ignore match) — explicitly
///    redacts its whole subtree. Always wins.
/// 2. [LDUnmask] (or a config-based unmask match) — suppresses *global* masking
///    within its subtree, but never overrides an enclosing [LDMask]/[LDIgnore].
/// 3. Global config — the `maskTextInputs` / `maskLabels` / `maskImages` /
///    `maskWebViews` privacy flags, matched against the widget that renders the
///    corresponding content.
class MaskingPolicy {
  const MaskingPolicy({
    required this.maskTextInputs,
    this.maskLabels = false,
    this.maskImages = false,
    this.maskWebViews = false,
    this.minimumAlpha = 0.02,
    this.widgetConfig = WidgetMaskingConfig.empty,
  });

  /// Redacts editable text fields (`EditableText`, e.g. `TextField`).
  final bool maskTextInputs;

  /// Redacts static text labels (`RichText`, which backs `Text`/`Text.rich`).
  final bool maskLabels;

  /// Redacts raster images (`RawImage`, which backs `Image`/`Image.*`).
  final bool maskImages;

  /// Redacts embedded platform views (where web views are hosted).
  final bool maskWebViews;

  /// Opacity threshold below which a widget is treated as invisible and pruned
  /// from the walk (neither captured nor masked). A widget that is at least this
  /// opaque still participates. Matches the native pruning threshold.
  final double minimumAlpha;

  /// Config-based per-widget rules matched by [Key] / runtime [Type], the
  /// counterpart of the [LDMask] / [LDUnmask] / [LDIgnore] markers.
  final WidgetMaskingConfig widgetConfig;

  /// Starts an explicit, type-agnostic mask covering the widget's subtree. Both
  /// the [LDMask] and [LDIgnore] markers and a config-based mask/ignore match
  /// qualify — in Flutter an ignore is realized as a cover, like a mask.
  bool isExplicitMask(Widget widget) {
    if (widget is LDMask || widget is LDIgnore) {
      return true;
    }
    if (widgetConfig.isEmpty) {
      return false;
    }
    if (widgetConfig.maskTypes.contains(widget.runtimeType) ||
        widgetConfig.ignoreTypes.contains(widget.runtimeType)) {
      return true;
    }
    final key = widget.key;
    return key != null &&
        (widgetConfig.maskKeys.contains(key) ||
            widgetConfig.ignoreKeys.contains(key));
  }

  /// Starts a region that is exempt from global masking.
  bool isUnmask(Widget widget) {
    if (widget is LDUnmask) {
      return true;
    }
    if (widgetConfig.isEmpty) {
      return false;
    }
    if (widgetConfig.unmaskTypes.contains(widget.runtimeType)) {
      return true;
    }
    final key = widget.key;
    return key != null && widgetConfig.unmaskKeys.contains(key);
  }

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
