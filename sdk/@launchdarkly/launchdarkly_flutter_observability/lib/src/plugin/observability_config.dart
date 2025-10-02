import 'package:flutter/foundation.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

const _defaultOtlpEndpoint =
    'https://otel.observability.app.launchdarkly.com:4318';

const _defaultBackendUrl = 'https://pub.observability.app.launchdarkly.com';

// Implementation note: The final values with defaults should be included
// in the configuration. This centralizes the assignment of defaults versus
// having them in each location that requires them.

// Implementation note: Use classes for instrumentation settings to allow them
// to be extended in the future. For example a logging setting may start as
// enabled/disabled, but could evolve to requiring filters or other advanced
// configuration. Using classes allows us to extend this functionality in
// a non-breaking way. If you want to enumerate settings use a final base
// class to prevent a user from doing exhaustive matching. If you can represent
// the state safely without a union, then just use factory constructors to
// represent the potential options.

/// Configuration for the debugPrint instrumentation.
final class DebugPrintSetting {
  const DebugPrintSetting._internal();

  /// Only record debugPrint statements in a release configuration.
  ///
  /// By convention most debug prints should be guarded by [kDebugMode], so
  /// very few should be present in release.
  ///
  /// When this setting is enabled debugPrint statements will not be forwarded
  /// to the default handler in a release configuration. They will not appear
  /// in the flutter tools or console. They will still be in debug.
  factory DebugPrintSetting.releaseOnly() {
    return const DebugPrintReleaseOnly();
  }

  /// Record debugPrint statements in any configuration.
  ///
  /// Depending on the application this could result in a high volume of
  /// log messages.
  ///
  /// When this setting is enabled debugPrint statements will not be forwarded
  /// to the default handler in any configuration. They will not appear
  /// in the flutter tools or console.
  factory DebugPrintSetting.always() {
    return const DebugPrintAlways();
  }

  /// Do not instrument debugPrint.
  factory DebugPrintSetting.disabled() {
    return const DebugPrintDisabled();
  }
}

/// Not for export.
/// Should be created using the factories for DebugPrintSetting.
final class DebugPrintReleaseOnly extends DebugPrintSetting {
  const DebugPrintReleaseOnly() : super._internal();
}

/// Not for export.
/// Should be created using the factories for DebugPrintSetting.
final class DebugPrintAlways extends DebugPrintSetting {
  const DebugPrintAlways() : super._internal();
}

/// Not for export.
/// Should be created using the factories for DebugPrintSetting.
final class DebugPrintDisabled extends DebugPrintSetting {
  const DebugPrintDisabled() : super._internal();
}

/// Configuration for instrumentations.
final class InstrumentationConfig {
  /// Configuration for the debug print instrumentation.
  ///
  /// Defaults to [DebugPrintSetting.releaseOnly].
  final DebugPrintSetting debugPrint;

  /// Construct an instrumentation configuration.
  ///
  /// [InstrumentationConfig.debugPrint] Controls the the instrumentation
  /// of `debugPrint`.
  InstrumentationConfig({this.debugPrint = const DebugPrintReleaseOnly()});
}

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

  /// Configuration of instrumentations.
  final InstrumentationConfig instrumentationConfig;

  ObservabilityConfig({
    this.applicationName,
    this.applicationVersion,
    required this.otlpEndpoint,
    required this.backendUrl,
    required this.instrumentationConfig,
    this.contextFriendlyName,
  });
}

ObservabilityConfig configWithDefaults({
  String? applicationName,
  String? applicationVersion,
  String? otlpEndpoint,
  String? backendUrl,
  String? Function(LDContext context)? contextFriendlyName,
  InstrumentationConfig? instrumentationConfig,
}) {
  return ObservabilityConfig(
    applicationName: applicationName,
    applicationVersion: applicationVersion,
    otlpEndpoint: otlpEndpoint ?? _defaultOtlpEndpoint,
    backendUrl: backendUrl ?? _defaultBackendUrl,
    contextFriendlyName: contextFriendlyName,
    instrumentationConfig: instrumentationConfig ?? InstrumentationConfig(),
  );
}
