// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/replay/plugin/SessionReplayOptions.cs.

/// Configuration for the LaunchDarkly session replay plugin. Mirrors
/// `LaunchDarkly.SessionReplay.SessionReplayOptions` in the .NET bridge.
///
/// Platform-agnostic: the native wire conversion lives in
/// `platform/io/native_options_codec.dart`.
class SessionReplayOptions {
  final bool isEnabled;
  final String serviceName;
  final PrivacyOptions privacy;

  const SessionReplayOptions({
    this.isEnabled = true,
    this.serviceName = 'sessionreplay-flutter',
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

  const PrivacyOptions({
    this.maskTextInputs = true,
    this.maskWebViews = false,
    this.maskLabels = false,
    this.maskImages = false,
    this.minimumAlpha = 0.02,
  });
}
