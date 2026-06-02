// Native (pigeon) wire conversions for the public option types. Kept under
// platform/io so the public option classes stay free of pigeon/messages.g.dart
// and remain compilable on web.

import '../../options/observability_options.dart';
import '../../options/session_replay_options.dart';
import 'messages.g.dart' as wire;

extension ObservabilityOptionsWire on ObservabilityOptions {
  wire.LDObservabilityOptions toWire() => wire.LDObservabilityOptions(
    isEnabled: isEnabled,
    serviceName: serviceName,
    serviceVersion: serviceVersion,
    otlpEndpoint: otlpEndpoint,
    backendUrl: backendUrl,
    contextFriendlyName: contextFriendlyName,
    attributes: attributes,
    instrumentation: instrumentation.toWire(),
  );
}

extension InstrumentationOptionsWire on InstrumentationOptions {
  wire.LDInstrumentationOptions toWire() => wire.LDInstrumentationOptions(
    networkRequests: networkRequests,
    launchTimes: launchTimes,
  );
}

extension SessionReplayOptionsWire on SessionReplayOptions {
  wire.LDSessionReplayOptions toWire() => wire.LDSessionReplayOptions(
    isEnabled: isEnabled,
    serviceName: serviceName,
    privacy: privacy.toWire(),
  );
}

extension PrivacyOptionsWire on PrivacyOptions {
  wire.LDPrivacyOptions toWire() => wire.LDPrivacyOptions(
    maskTextInputs: maskTextInputs,
    maskWebViews: maskWebViews,
    maskLabels: maskLabels,
    maskImages: maskImages,
    minimumAlpha: minimumAlpha,
  );
}
