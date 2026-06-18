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
    customHeaders: customHeaders,
    sessionBackgroundTimeoutMillis: sessionBackgroundTimeout.inMilliseconds,
    logsApiLevel: logsApiLevel.severity,
    traces: traces.toWire(),
    metricsEnabled: metricsEnabled,
    analytics: analytics.toWire(),
    instrumentation: instrumentation.toWire(),
  );
}

extension TracesOptionsWire on TracesOptions {
  wire.LDTracesOptions toWire() => wire.LDTracesOptions(
    includeErrors: includeErrors,
    includeSpans: includeSpans,
  );
}

extension AnalyticsOptionsWire on AnalyticsOptions {
  wire.LDAnalyticsOptions toWire() => wire.LDAnalyticsOptions(
    taps: taps,
    views: views,
    trackEvents: trackEvents,
    appLifecycle: appLifecycle,
    appLaunch: appLaunch,
  );
}

extension InstrumentationOptionsWire on InstrumentationOptions {
  wire.LDInstrumentationOptions toWire() => wire.LDInstrumentationOptions(
    networkRequests: networkRequests,
    launchTimes: launchTimes,
    crashReporting: crashReporting,
  );
}

extension SessionReplayOptionsWire on SessionReplayOptions {
  wire.LDSessionReplayOptions toWire() => wire.LDSessionReplayOptions(
    isEnabled: isEnabled,
    serviceName: serviceName,
    frameRate: frameRate,
    scale: scale,
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
