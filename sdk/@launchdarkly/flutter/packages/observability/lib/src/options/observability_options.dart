// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/plugin/ObservabilityOptions.cs.

import '../plugin/observability_config.dart';

/// Toggles for SDK-side instrumentation. Mirrors `InstrumentationOptions` in
/// the .NET MAUI bridge, extended with the Dart-only [debugPrint] control.
class InstrumentationOptions {
  /// Whether to instrument network requests (native bridge only).
  final bool networkRequests;

  /// Whether to instrument launch times (native bridge only).
  final bool launchTimes;

  /// Controls instrumentation of `debugPrint` in the Dart OpenTelemetry
  /// pipeline. Defaults to [DebugPrintSetting.releaseOnly].
  final DebugPrintSetting debugPrint;

  const InstrumentationOptions({
    this.networkRequests = true,
    this.launchTimes = true,
    this.debugPrint = const DebugPrintReleaseOnly(),
  });
}

/// Configuration for the LaunchDarkly observability plugin. Field set mirrors
/// `LaunchDarkly.Observability.ObservabilityOptions` in the .NET bridge.
///
/// This type is platform-agnostic on purpose: it carries no transport details
/// (no pigeon wire types), so it can be passed to either the native (mobile)
/// or web implementation. The native wire conversion lives in
/// `platform/io/native_options_codec.dart`.
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
}
