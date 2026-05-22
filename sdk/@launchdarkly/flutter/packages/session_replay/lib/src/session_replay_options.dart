// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/replay/plugin/SessionReplayOptions.cs.

import 'messages.g.dart' as wire;

/// Configuration for the LaunchDarkly session replay plugin. Mirrors
/// `LaunchDarkly.SessionReplay.SessionReplayOptions` in the .NET bridge.
class SessionReplayOptions {
  final bool isEnabled;
  final String serviceName;
  final PrivacyOptions privacy;

  const SessionReplayOptions({
    this.isEnabled = true,
    this.serviceName = 'sessionreplay-flutter',
    this.privacy = const PrivacyOptions(),
  });

  wire.LDSessionReplayOptions toWire() => wire.LDSessionReplayOptions(
    isEnabled: isEnabled,
    serviceName: serviceName,
    privacy: privacy.toWire(),
  );
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

  wire.LDPrivacyOptions toWire() => wire.LDPrivacyOptions(
    maskTextInputs: maskTextInputs,
    maskWebViews: maskWebViews,
    maskLabels: maskLabels,
    maskImages: maskImages,
    minimumAlpha: minimumAlpha,
  );
}
