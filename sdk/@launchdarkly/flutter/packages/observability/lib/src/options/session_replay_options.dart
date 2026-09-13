// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/replay/plugin/SessionReplayOptions.cs.

import 'package:flutter/widgets.dart' show Key;

/// Configuration for the LaunchDarkly session replay plugin. Mirrors
/// `LaunchDarkly.SessionReplay.SessionReplayOptions` in the .NET bridge.
///
/// Platform-agnostic: the native wire conversion lives in
/// `platform/io/native_options_codec.dart`.
class SessionReplayOptions {
  final bool isEnabled;
  final String serviceName;

  /// Probability from `0.0` to `1.0` that Session Replay starts when enabled.
  /// Values at or below zero never record; values at or above one always
  /// record. The native SDK makes a new decision each time replay is enabled.
  final double sampleRate;

  /// Target capture rate in frames per second. Mirrors Android/iOS
  /// `frameRate`. Native-only. Defaults to `1.0`.
  final double frameRate;

  /// Replay capture scale. Controls the resolution frames are captured and
  /// exported at: `1.0` = 1x (160 DPI), `2.0` = 2x, etc. Higher values capture
  /// more detail at the cost of larger frames. Mirrors Android
  /// `ReplayOptions.scale` and iOS `SessionReplayOptions.scale`. `null` is
  /// treated as `1.0`. Defaults to `1.0`.
  final double? scale;

  /// JPEG encoding quality of exported frames, from `0.0` (lowest quality,
  /// smallest payload) to `1.0` (highest quality, largest payload). Values
  /// outside that range are clamped by the native SDK. Mirrors Android
  /// `ReplayOptions.imageQuality` and iOS `SessionReplayOptions.imageQuality`.
  /// Native-only. Defaults to `0.3`.
  final double imageQuality;

  final PrivacyOptions privacy;

  const SessionReplayOptions({
    this.isEnabled = true,
    this.serviceName = 'sessionreplay-flutter',
    this.sampleRate = 1.0,
    this.frameRate = 1.0,
    this.scale = 1.0,
    this.imageQuality = 0.3,
    this.privacy = const PrivacyOptions(),
  });
}

/// Privacy controls for session replay. Mirrors
/// `SessionReplayOptions.PrivacyOptions` in the .NET bridge.
class PrivacyOptions {
  final bool maskTextInputs;
  final bool maskWebViews;
  final bool maskLabels;
  final bool maskImages;
  final double minimumAlpha;

  /// Widget runtime [Type]s to mask (cover) wherever they appear, e.g.
  /// `{MyCardWidget}`. Resolved on the Flutter side only — not sent to native.
  final Set<Type> maskWidgetTypes;

  /// Widget [Key]s to mask (cover) wherever they appear. Dart-side only.
  final Set<Key> maskWidgetKeys;

  /// Widget runtime [Type]s to reveal from *global* masking. Does not override
  /// an enclosing explicit mask/ignore. Dart-side only.
  final Set<Type> unmaskWidgetTypes;

  /// Widget [Key]s to reveal from global masking. Dart-side only.
  final Set<Key> unmaskWidgetKeys;

  /// Widget runtime [Type]s to ignore. In Flutter this covers the region (the
  /// single-raster capture can't drop pixels). Dart-side only.
  final Set<Type> ignoreWidgetTypes;

  /// Widget [Key]s to ignore (cover). Dart-side only.
  final Set<Key> ignoreWidgetKeys;

  const PrivacyOptions({
    this.maskTextInputs = true,
    this.maskWebViews = false,
    this.maskLabels = false,
    this.maskImages = false,
    this.minimumAlpha = 0.02,
    this.maskWidgetTypes = const {},
    this.maskWidgetKeys = const {},
    this.unmaskWidgetTypes = const {},
    this.unmaskWidgetKeys = const {},
    this.ignoreWidgetTypes = const {},
    this.ignoreWidgetKeys = const {},
  });
}
