// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/plugin/ObservabilityOptions.cs.

import '../instrumentation/click/ld_click.dart';
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
/// [taps] and [views] map to the native `analytics.taps` / `analytics.screenViews`
/// publish gates on both iOS and Android. [trackEvents] gates the `track` span
/// emitted by the cross-platform Dart pipeline (web and mobile) as well as the
/// native Android span.
class AnalyticsOptions {
  /// Whether to capture taps and publish a `click` event for each.
  ///
  /// Flutter draws its whole UI into one native view, so the tapped widget is
  /// resolved in Dart (see `SessionReplayCapture`, which hosts the detector) and
  /// reported to the native SDK; this flag gates that detection as well as the
  /// native publish gates it drives (`analytics.taps` and
  /// `instrumentation.userTaps`). On mobile the `click` span is gated by this
  /// flag while the Session Replay `Click` event is emitted regardless — matching
  /// how [views] treats `Navigate`. Defaults to `true`.
  final bool taps;

  /// Whether to emit spans for screen/page views. Maps to the native
  /// `analytics.screenViews` gate on Android and iOS; a no-op on web. Screen
  /// views are reported from Dart (see `LDNavigatorObserver`), so this gates the
  /// `screen_view` span only — the Session Replay `Navigate` event is emitted
  /// regardless. Defaults to `true`.
  final bool views;

  /// Whether to emit a `track` span when a custom event is tracked, either
  /// through the LaunchDarkly client's `afterTrack` hook or the manual
  /// `LDObserve.track` API. Defaults to `true`.
  final bool trackEvents;

  /// Whether to emit app-lifecycle spans (`app_foreground` / `app_background`)
  /// as the app moves between foreground and background. Mobile-only (iOS,
  /// Android); a no-op elsewhere. Defaults to `true`.
  final bool appLifecycle;

  /// Whether to emit an `app_launch` span (carrying `event.launch_type` —
  /// `install` / `update` / `relaunch` — and version fields) once per process
  /// launch. Mobile-only (iOS, Android); a no-op elsewhere. Defaults to `true`.
  final bool appLaunch;

  /// Recognizes the application's own widget types as click targets, so a
  /// design-system `PrimaryButton` reports itself instead of the anonymous
  /// `InkWell` it is built from.
  ///
  /// Registers a type once for the whole app, where the `LDClick` widget names a
  /// single instance. Dart-side only — it runs while the tapped widget is
  /// resolved, so it is not sent to native.
  ///
  /// ```dart
  /// AnalyticsOptions(
  ///   customClickTargetResolver: (widget) => switch (widget) {
  ///     PrimaryButton(:final label) =>
  ///       LDClickTargetInfo(tag: 'PrimaryButton', text: label),
  ///     _ => null,
  ///   },
  /// )
  /// ```
  final LDClickTargetResolver? customClickTargetResolver;

  const AnalyticsOptions({
    this.taps = true,
    this.views = true,
    this.trackEvents = true,
    this.appLifecycle = true,
    this.appLaunch = true,
    this.customClickTargetResolver,
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
    views: false,
    trackEvents: false,
    appLifecycle: false,
    appLaunch: false,
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

  /// Returns a copy with the given fields replaced. Only non-null arguments
  /// override; existing values (including [attributes]) are otherwise preserved.
  ObservabilityOptions copyWith({
    bool? isEnabled,
    String? serviceName,
    String? serviceVersion,
    String? otlpEndpoint,
    String? backendUrl,
    String? contextFriendlyName,
    Map<String, Object?>? attributes,
    Map<String, String>? customHeaders,
    Duration? sessionBackgroundTimeout,
    ObservabilityLogLevel? logsApiLevel,
    TracesOptions? traces,
    bool? metricsEnabled,
    AnalyticsOptions? analytics,
    InstrumentationOptions? instrumentation,
  }) {
    return ObservabilityOptions(
      isEnabled: isEnabled ?? this.isEnabled,
      serviceName: serviceName ?? this.serviceName,
      serviceVersion: serviceVersion ?? this.serviceVersion,
      otlpEndpoint: otlpEndpoint ?? this.otlpEndpoint,
      backendUrl: backendUrl ?? this.backendUrl,
      contextFriendlyName: contextFriendlyName ?? this.contextFriendlyName,
      attributes: attributes ?? this.attributes,
      customHeaders: customHeaders ?? this.customHeaders,
      sessionBackgroundTimeout:
          sessionBackgroundTimeout ?? this.sessionBackgroundTimeout,
      logsApiLevel: logsApiLevel ?? this.logsApiLevel,
      traces: traces ?? this.traces,
      metricsEnabled: metricsEnabled ?? this.metricsEnabled,
      analytics: analytics ?? this.analytics,
      instrumentation: instrumentation ?? this.instrumentation,
    );
  }
}
