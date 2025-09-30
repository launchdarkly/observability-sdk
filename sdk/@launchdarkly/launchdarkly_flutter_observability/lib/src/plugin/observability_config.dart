import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

const _defaultOtlpEndpoint =
    'https://otel.observability.app.launchdarkly.com:4318';

const _defaultBackendUrl = 'https://pub.observability.app.launchdarkly.com';

// Implementation note: The final values with defaults should be included
// in the configuration. This centralizes the assignment of defaults versus
// having them in each location that requires them.

final class ObservabilityConfig {
  /// The configured OTLP endpoint.
  final String otlpEndpoint;

  /// The configured back-end URL.
  final String backendUrl;

  /// The name of the application.
  final String? applicationName;

  /// The version of the application.
  ///
  /// This is commonly a Git hash or a semantic version.
  final String? applicationVersion;

  /// Function for mapping context to a friendly name for use in the
  /// observability UI.
  final String? Function(LDContext context)? contextFriendlyName;

  ObservabilityConfig({
    this.applicationName,
    this.applicationVersion,
    required this.otlpEndpoint,
    required this.backendUrl,
    this.contextFriendlyName,
  });
}

ObservabilityConfig configWithDefaults({
  String? applicationName,
  String? applicationVersion,
  String? otlpEndpoint,
  String? backendUrl,
  String? Function(LDContext context)? contextFriendlyName,
}) {
  return ObservabilityConfig(
    applicationName: applicationName,
    applicationVersion: applicationVersion,
    otlpEndpoint: otlpEndpoint ?? _defaultOtlpEndpoint,
    backendUrl: backendUrl ?? _defaultBackendUrl,
    contextFriendlyName: contextFriendlyName,
  );
}
