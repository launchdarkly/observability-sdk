// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/plugin/ObservabilityOptions.cs.

import 'messages.g.dart' as wire;

/// Toggles for SDK-side instrumentation. Mirrors `InstrumentationOptions` in
/// the .NET MAUI bridge.
class InstrumentationOptions {
  final bool networkRequests;
  final bool launchTimes;

  const InstrumentationOptions({
    this.networkRequests = true,
    this.launchTimes = true,
  });

  wire.LDInstrumentationOptions toWire() => wire.LDInstrumentationOptions(
    networkRequests: networkRequests,
    launchTimes: launchTimes,
  );
}

/// Configuration for the LaunchDarkly observability plugin. Field set mirrors
/// `LaunchDarkly.Observability.ObservabilityOptions` in the .NET bridge.
class ObservabilityOptions {
  static const String defaultServiceName = 'observability-flutter';
  static const String defaultServiceVersion = '0.1.0';
  static const String defaultOtlpEndpoint =
      'https://otel.observability.app.launchdarkly.com:4318';
  static const String defaultBackendUrl =
      'https://pub.observability.app.launchdarkly.com';

  final bool isEnabled;
  final String serviceName;
  final String serviceVersion;
  final String otlpEndpoint;
  final String backendUrl;
  final String? contextFriendlyName;
  final Map<String, Object?>? attributes;
  final InstrumentationOptions instrumentation;

  const ObservabilityOptions({
    this.isEnabled = true,
    this.serviceName = defaultServiceName,
    this.serviceVersion = defaultServiceVersion,
    String? otlpEndpoint,
    String? backendUrl,
    this.contextFriendlyName,
    this.attributes,
    this.instrumentation = const InstrumentationOptions(),
  }) : otlpEndpoint = otlpEndpoint ?? defaultOtlpEndpoint,
       backendUrl = backendUrl ?? defaultBackendUrl;

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
