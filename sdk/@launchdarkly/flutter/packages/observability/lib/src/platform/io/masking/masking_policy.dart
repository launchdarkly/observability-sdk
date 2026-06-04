import 'package:flutter/widgets.dart';

import '../../../masking.dart';

/// Pure rule engine that decides, per widget, how it participates in masking.
///
/// This is the Flutter counterpart of the native `MaskingPolicy`. It does not
/// walk the tree (that is [MaskCollector]'s job); it only answers per-element
/// questions based on the screen-wide [SessionReplayOptions] privacy settings
/// and the per-widget [LDMask] / [LDUnmask] markers.
///
/// Precedence (highest first):
/// 1. [LDMask] — explicitly redacts its whole subtree. Always wins.
/// 2. [LDUnmask] — suppresses *global* masking within its subtree, but never
///    overrides an enclosing [LDMask].
/// 3. Global config — e.g. `maskTextInputs` redacts every [EditableText].
class MaskingPolicy {
  const MaskingPolicy({required this.maskTextInputs});

  /// Whether screen-wide masking of text input fields is enabled.
  final bool maskTextInputs;

  /// Starts an explicit, type-agnostic mask covering the widget's subtree.
  bool isExplicitMask(Widget widget) => widget is LDMask;

  /// Starts a region that is exempt from global masking.
  bool isUnmask(Widget widget) => widget is LDUnmask;

  /// Whether [widget] is redacted purely by the global privacy config (i.e.
  /// when no explicit marker applies).
  bool isGloballyMasked(Widget widget) =>
      maskTextInputs && widget is EditableText;
}
