// Native (pigeon) wire conversions for the public option types. Kept under
// platform/io so the public option classes stay free of pigeon/messages.g.dart
// and remain compilable on web.

import '../../options/observability_options.dart';
import '../../options/session_replay_options.dart';
import 'messages.g.dart' as wire;

/// Converts [ObservabilityOptions] to its pigeon wire representation.
extension ObservabilityOptionsWire on ObservabilityOptions {
  /// Returns the equivalent `LDObservabilityOptions` for the native bridge.
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

/// Converts [TracesOptions] to its pigeon wire representation.
extension TracesOptionsWire on TracesOptions {
  /// Returns the equivalent `LDTracesOptions` for the native bridge.
  wire.LDTracesOptions toWire() => wire.LDTracesOptions(
    includeErrors: includeErrors,
    includeSpans: includeSpans,
  );
}

/// Converts [AnalyticsOptions] to its pigeon wire representation.
extension AnalyticsOptionsWire on AnalyticsOptions {
  /// Returns the equivalent `LDAnalyticsOptions` for the native bridge.
  wire.LDAnalyticsOptions toWire() => wire.LDAnalyticsOptions(
    taps: taps,
    views: views,
    trackEvents: trackEvents,
    appLifecycle: appLifecycle,
    appLaunch: appLaunch,
  );
}

/// Converts [InstrumentationOptions] to its pigeon wire representation.
extension InstrumentationOptionsWire on InstrumentationOptions {
  /// Returns the equivalent `LDInstrumentationOptions` for the native bridge.
  wire.LDInstrumentationOptions toWire() => wire.LDInstrumentationOptions(
    launchTimes: launchTimes,
    crashReporting: crashReporting,
  );
}

/// Converts [SessionReplayOptions] to its pigeon wire representation.
extension SessionReplayOptionsWire on SessionReplayOptions {
  /// Returns the equivalent `LDSessionReplayOptions` for the native bridge.
  wire.LDSessionReplayOptions toWire() => wire.LDSessionReplayOptions(
    isEnabled: isEnabled,
    serviceName: serviceName,
    sampleRate: sampleRate,
    frameRate: frameRate,
    scale: scale,
    imageQuality: imageQuality,
    privacy: privacy.toWire(),
  );
}

/// Converts [PrivacyOptions] to its pigeon wire representation.
extension PrivacyOptionsWire on PrivacyOptions {
  /// Returns the equivalent `LDPrivacyOptions` for the native bridge.
  wire.LDPrivacyOptions toWire() => wire.LDPrivacyOptions(
    maskTextInputs: maskTextInputs,
    maskWebViews: maskWebViews,
    maskLabels: maskLabels,
    maskImages: maskImages,
    minimumAlpha: minimumAlpha,
  );
}
