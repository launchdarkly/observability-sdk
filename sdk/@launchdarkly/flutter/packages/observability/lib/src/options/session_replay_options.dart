// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/replay/plugin/SessionReplayOptions.cs.

import 'package:flutter/widgets.dart' show Key;

/// Configuration for the LaunchDarkly session replay plugin. Mirrors
/// `LaunchDarkly.SessionReplay.SessionReplayOptions` in the .NET bridge.
///
/// Platform-agnostic: the native wire conversion lives in
/// `platform/io/native_options_codec.dart`.
class SessionReplayOptions {
  /// Whether Session Replay records on iOS and Android. Defaults to `true`.
  /// Session Replay is not supported on web, where this has no effect.
  final bool isEnabled;

  /// The `service.name` reported with Session Replay's own telemetry.
  /// Defaults to `sessionreplay-flutter`.
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

  /// What is masked in recorded frames and click events.
  final PrivacyOptions privacy;

  /// Creates Session Replay options. The defaults record every session at one
  /// frame per second with text inputs masked.
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
  /// Covers editable text fields in recorded frames. Defaults to `true`.
  final bool maskTextInputs;

  /// Covers web views in recorded frames. Defaults to `false`.
  final bool maskWebViews;

  /// Covers non-editable text in recorded frames. Defaults to `false`.
  final bool maskLabels;

  /// Covers images in recorded frames. Defaults to `false`.
  final bool maskImages;

  /// Opacity below which a view is treated as invisible and not masked, from
  /// `0.0` to `1.0`. Defaults to `0.02`.
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

  /// Redacts the visible text of tapped widgets, so clicks report the widget
  /// type and identifier but no label.
  ///
  /// Off by default, matching what the native SDKs already capture for native
  /// views. Turn it on when button labels themselves are sensitive — a list of
  /// diagnoses, say, where "which row" is as revealing as the row's contents.
  ///
  /// Independent of [maskLabels], which controls whether text is *painted over*
  /// in the recorded frames. This one controls the `clickTextContent` replay
  /// field and the `event.text` span attribute, so a screen can be readable in
  /// replay while its click stream stays anonymous, or the reverse.
  ///
  /// Narrower redactions apply regardless of this flag: text is never read from
  /// an [LDMask]/[LDIgnore] subtree or from an editable field's contents.
  ///
  /// Dart-side only — resolved while the tapped widget is identified, so it is
  /// not sent to native.
  final bool maskClickText;

  /// Creates privacy options. By default only text inputs are masked.
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
    this.maskClickText = false,
  });
}
