// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/plugin/ObservabilityOptions.cs.

import '../plugin/observability_config.dart';

/// Severity threshold for exported logs. Mirrors the `LogLevel` enums in the
/// Android (`ObservabilityOptions.LogLevel`) and iOS (`ObservabilityOptions.LogLevel`)
/// SDKs. The [severity] values match the OpenTelemetry log severity numbers so
/// they can be forwarded across the native bridge unchanged.
///
/// Native-only: the web/Dart pipeline currently ignores this value.
enum ObservabilityLogLevel {
  trace(1),
  trace2(2),
  trace3(3),
  trace4(4),
  debug(5),
  debug2(6),
  debug3(7),
  debug4(8),
  info(9),
  info2(10),
  info3(11),
  info4(12),
  warn(13),
  warn2(14),
  warn3(15),
  warn4(16),
  error(17),
  error2(18),
  error3(19),
  error4(20),
  fatal(21),
  fatal2(22),
  fatal3(23),
  fatal4(24),

  /// Disables log exporting entirely.
  none(0x7fffffff);

  /// The OpenTelemetry log severity number for this level.
  final int severity;

  const ObservabilityLogLevel(this.severity);
}

/// Controls automatic trace generation. Mirrors `TracesApi` (Android) and
/// `AppTracing` (iOS).
///
/// Native-only: the web/Dart pipeline currently ignores these values.
class TracesOptions {
  /// Whether to automatically record errors and exceptions as spans.
  final bool includeErrors;

  /// Whether to automatically record UI performance and other events as spans.
  final bool includeSpans;

  const TracesOptions({this.includeErrors = true, this.includeSpans = true});
}

/// Toggles for SDK-side instrumentation. Mirrors `InstrumentationOptions` in
/// the .NET MAUI bridge, extended with the Dart-only [debugPrint] control.
class InstrumentationOptions {
  /// Whether to instrument network requests (native bridge only).
  final bool networkRequests;

  /// Whether to instrument launch times (native bridge only).
  final bool launchTimes;

  /// Whether to automatically report uncaught exceptions as errors. Mirrors
  /// Android `Instrumentations.crashReporting` and iOS `CrashReporting`.
  ///
  /// Native-only. Defaults to `true` to match the native SDK defaults.
  final bool crashReporting;

  /// Controls instrumentation of `debugPrint` in the Dart OpenTelemetry
  /// pipeline. Defaults to [DebugPrintSetting.releaseOnly].
  final DebugPrintSetting debugPrint;

  const InstrumentationOptions({
    this.networkRequests = true,
    this.launchTimes = true,
    this.crashReporting = true,
    this.debugPrint = const DebugPrintReleaseOnly(),
  });
}

/// Analytics telemetry emitted as OpenTelemetry spans. Mirrors Android
/// `ObservabilityOptions.Analytics`.
///
/// [taps] maps to the native `analytics.taps` publish gate on both iOS and
/// Android; [pageViews] is Android-only and a no-op elsewhere. [trackEvents]
/// gates the `track` span emitted by the cross-platform Dart pipeline (web and
/// mobile) as well as the native Android span.
class AnalyticsOptions {
  /// Whether to publish a `click` span for each detected tap. Tap detection is
  /// always enabled on the native side (`instrumentation.userTaps`); this flag
  /// only controls publishing the OpenTelemetry span. Defaults to `true`.
  final bool taps;

  /// Whether to start spans for screen/page view lifecycle events. Android-only.
  /// Defaults to `true`.
  final bool pageViews;

  /// Whether to emit a `track` span when a custom event is tracked, either
  /// through the LaunchDarkly client's `afterTrack` hook or the manual
  /// `LDObserve.track` API. Defaults to `true`.
  final bool trackEvents;

  const AnalyticsOptions({
    this.taps = true,
    this.pageViews = true,
    this.trackEvents = true,
  });

  /// All analytics telemetry enabled. Convenience mirroring Swift's
  /// `Analytics.enabled`; equivalent to `const AnalyticsOptions()`.
  ///
  /// ```dart
  /// ObservabilityOptions(analytics: AnalyticsOptions.enabled)
  /// ```
  static const AnalyticsOptions enabled = AnalyticsOptions();

  /// All analytics telemetry disabled. Convenience mirroring Swift's
  /// `Analytics.disabled`.
  ///
  /// ```dart
  /// ObservabilityOptions(analytics: AnalyticsOptions.disabled)
  /// ```
  static const AnalyticsOptions disabled = AnalyticsOptions(
    taps: false,
    pageViews: false,
    trackEvents: false,
  );
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

  /// Extra HTTP headers added to OTLP exports (e.g. for proxies or auth).
  /// Mirrors Android/iOS `customHeaders`. Native-only.
  final Map<String, String> customHeaders;

  /// How long the app may stay in the background before the current session is
  /// ended. Mirrors Android/iOS `sessionBackgroundTimeout`. Native-only.
  /// Defaults to 15 minutes.
  final Duration sessionBackgroundTimeout;

  /// Minimum severity of logs forwarded to the OpenTelemetry logs pipeline.
  /// Use [ObservabilityLogLevel.none] to disable logs. Mirrors Android/iOS
  /// `logsApiLevel`. Native-only. Defaults to [ObservabilityLogLevel.info].
  final ObservabilityLogLevel logsApiLevel;

  /// Controls automatic trace generation. Mirrors Android `tracesApi` and iOS
  /// `tracesApi`. Native-only.
  final TracesOptions traces;

  /// Whether to export metrics. Mirrors Android `metricsApi` and iOS
  /// `metricsApi`. Native-only. Defaults to `true`.
  final bool metricsEnabled;

  /// Analytics telemetry configuration. Mirrors Android
  /// `ObservabilityOptions.analytics`. Native-only.
  final AnalyticsOptions analytics;

  final InstrumentationOptions instrumentation;

  const ObservabilityOptions({
    this.isEnabled = true,
    this.serviceName = defaultServiceName,
    this.serviceVersion = defaultServiceVersion,
    String? otlpEndpoint,
    String? backendUrl,
    this.contextFriendlyName,
    this.attributes,
    this.customHeaders = const {},
    this.sessionBackgroundTimeout = const Duration(minutes: 15),
    this.logsApiLevel = ObservabilityLogLevel.info,
    this.traces = const TracesOptions(),
    this.metricsEnabled = true,
    this.analytics = const AnalyticsOptions(),
    this.instrumentation = const InstrumentationOptions(),
  }) : otlpEndpoint = otlpEndpoint ?? defaultOtlpEndpoint,
       backendUrl = backendUrl ?? defaultBackendUrl;
}
