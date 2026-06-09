import 'package:flutter/widgets.dart';

/// Per-widget masking rules resolved entirely on the Dart side.
///
/// Unlike the boolean privacy flags (`maskTextInputs`, ...) these never cross
/// the native bridge — widget [Key]s and runtime [Type]s aren't serializable —
/// so the Flutter [MaskCollector] consults them directly while walking the
/// element tree. They are the config-based counterpart of the per-widget
/// [LDMask] / [LDUnmask] / [LDIgnore] markers: instead of wrapping a widget,
/// you name a widget's `Key` or `runtimeType` once in `PrivacyOptions`.
///
/// Precedence matches the markers and the native SDKs: a mask/ignore match wins
/// over an unmask match on the same widget, and an explicit mask/ignore covers
/// its whole subtree.
@immutable
class WidgetMaskingConfig {
  /// Widgets whose `runtimeType` is in this set are masked (covered).
  final Set<Type> maskTypes;

  /// Widgets whose `key` is in this set are masked (covered).
  final Set<Key> maskKeys;

  /// Widgets whose `runtimeType` is in this set are unmasked (revealed from
  /// global masking, but never from an enclosing explicit mask/ignore).
  final Set<Type> unmaskTypes;

  /// Widgets whose `key` is in this set are unmasked.
  final Set<Key> unmaskKeys;

  /// Widgets whose `runtimeType` is in this set are ignored. In Flutter this is
  /// equivalent to masking (the region is painted over) — see [LDIgnore].
  final Set<Type> ignoreTypes;

  /// Widgets whose `key` is in this set are ignored (covered).
  final Set<Key> ignoreKeys;

  const WidgetMaskingConfig({
    this.maskTypes = const {},
    this.maskKeys = const {},
    this.unmaskTypes = const {},
    this.unmaskKeys = const {},
    this.ignoreTypes = const {},
    this.ignoreKeys = const {},
  });

  /// A config with no rules; the common case (nothing to resolve Dart-side).
  static const WidgetMaskingConfig empty = WidgetMaskingConfig();

  /// `true` when no rules are configured, so the collector can skip the lookups.
  bool get isEmpty =>
      maskTypes.isEmpty &&
      maskKeys.isEmpty &&
      unmaskTypes.isEmpty &&
      unmaskKeys.isEmpty &&
      ignoreTypes.isEmpty &&
      ignoreKeys.isEmpty;
}

/// Process-wide active config, populated from `PrivacyOptions` when the SDK
/// starts and read by the capture widget when building its [MaskingPolicy].
///
/// A global holder (rather than a widget parameter) bridges the gap between
/// where the rules are configured — `LDObserve.start(...)` — and where the
/// capture widget is mounted in the tree, which don't share a constructor.
WidgetMaskingConfig activeWidgetMaskingConfig = WidgetMaskingConfig.empty;
