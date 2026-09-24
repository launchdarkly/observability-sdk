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
  /// OTel severity `TRACE` (1): export every log.
  trace(1),

  /// OTel severity `TRACE2` (2).
  trace2(2),

  /// OTel severity `TRACE3` (3).
  trace3(3),

  /// OTel severity `TRACE4` (4).
  trace4(4),

  /// OTel severity `DEBUG` (5).
  debug(5),

  /// OTel severity `DEBUG2` (6).
  debug2(6),

  /// OTel severity `DEBUG3` (7).
  debug3(7),

  /// OTel severity `DEBUG4` (8).
  debug4(8),

  /// OTel severity `INFO` (9). The default threshold.
  info(9),

  /// OTel severity `INFO2` (10).
  info2(10),

  /// OTel severity `INFO3` (11).
  info3(11),

  /// OTel severity `INFO4` (12).
  info4(12),

  /// OTel severity `WARN` (13).
  warn(13),

  /// OTel severity `WARN2` (14).
  warn2(14),

  /// OTel severity `WARN3` (15).
  warn3(15),

  /// OTel severity `WARN4` (16).
  warn4(16),

  /// OTel severity `ERROR` (17).
  error(17),

  /// OTel severity `ERROR2` (18).
  error2(18),

  /// OTel severity `ERROR3` (19).
  error3(19),

  /// OTel severity `ERROR4` (20).
  error4(20),

  /// OTel severity `FATAL` (21).
  fatal(21),

  /// OTel severity `FATAL2` (22).
  fatal2(22),

  /// OTel severity `FATAL3` (23).
  fatal3(23),

  /// OTel severity `FATAL4` (24).
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

  /// Creates trace options; both kinds of automatic spans are on by default.
  const TracesOptions({this.includeErrors = true, this.includeSpans = true});
}

/// Toggles for SDK-side instrumentation. Mirrors `InstrumentationOptions` in
/// the .NET MAUI bridge, extended with the Dart-only [debugPrint] control.
class InstrumentationOptions {
  /// Reserved for network request instrumentation. Currently has no effect:
  /// HTTP requests are not instrumented on any platform, because Flutter's
  /// HTTP clients go through `dart:io`, which native instrumentation cannot
  /// see.
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

  /// Creates instrumentation options; every instrumentation is on by default,
  /// with `debugPrint` captured in release builds only.
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

  /// Whether to emit spans for screen/page views. Screen views are reported from
  /// Dart (see `LDNavigatorObserver`); on web this gates the Dart `screen_view`
  /// span, and on Android and iOS it maps to the native `analytics.screenViews`
  /// gate. It gates the span only — the mobile Session Replay `Navigate` event is
  /// emitted regardless. Defaults to `true`.
  final bool views;

  /// Whether to emit a `track` span when a custom event is tracked, either
  /// through the LaunchDarkly client's `afterTrack` hook or the manual
  /// `LDObserve.track` API. Defaults to `true`.
  final bool trackEvents;

  /// Whether to emit app-lifecycle spans as the app changes state.
  ///
  /// On every platform this gates the Dart `device.app.lifecycle` span, emitted
  /// on each Flutter `AppLifecycleState` change (`resumed`, `inactive`,
  /// `hidden`, `paused`, `detached`). On iOS and Android it also gates the
  /// native `app_foreground` / `app_background` spans. Defaults to `true`.
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

  /// Creates analytics options; all analytics telemetry is on by default.
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
  /// The [serviceName] used when none is given.
  static const String defaultServiceName = 'observability-flutter';

  /// The LaunchDarkly OTLP collector used when no [otlpEndpoint] is given.
  static const String defaultOtlpEndpoint =
      'https://otel.observability.app.launchdarkly.com:4318';

  /// The LaunchDarkly observability backend used when no [backendUrl] is
  /// given.
  static const String defaultBackendUrl =
      'https://pub.observability.app.launchdarkly.com';

  /// Whether observability telemetry is recorded. Defaults to `true`.
  ///
  /// When `false`, no spans or logs are recorded from Dart: flag evaluations,
  /// `LDObserve.startSpan`, `recordException`, `recordLog`, `debugPrint` and
  /// lifecycle capture are all no-ops, and on web nothing is exported. Session
  /// replay is controlled separately by `SessionReplayOptions.isEnabled`; while
  /// it is on, screen views, clicks, track events and identifies are still
  /// forwarded to native, because the replay timeline is built from them. The
  /// flag is also passed to the native SDK for its automatic instrumentation.
  final bool isEnabled;

  /// The `service.name` resource attribute. Defaults to [defaultServiceName],
  /// which names the SDK rather than your app — set it to identify your app.
  final String serviceName;

  /// The `service.version` resource attribute, such as a release version or
  /// Git SHA.
  ///
  /// When `null` (the default), iOS and Android report the host app's version
  /// (`CFBundleShortVersionString` / `versionName`, which Flutter sets from the
  /// `version` in `pubspec.yaml`), and web omits `service.version`.
  final String? serviceVersion;

  /// The OTLP/HTTP endpoint telemetry is exported to (without the `/v1/...`
  /// signal path). Defaults to [defaultOtlpEndpoint]; override it to send data
  /// through a proxy.
  final String otlpEndpoint;

  /// The LaunchDarkly observability backend used for session and replay
  /// metadata. Defaults to [defaultBackendUrl].
  final String backendUrl;

  /// A human-readable name for the current context, shown in the
  /// observability UI instead of its key.
  final String? contextFriendlyName;

  /// Extra OTel Resource attributes attached to every exported signal on
  /// every platform, such as `deployment.environment`.
  ///
  /// Use strings, numbers, booleans, or homogeneous lists of those; other
  /// values are not portable across platforms. Use [serviceName] and
  /// [serviceVersion] rather than the `service.*` keys, which the SDK sets
  /// itself.
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
  /// `ObservabilityOptions.analytics`. See each [AnalyticsOptions] field for
  /// which platforms it applies to.
  final AnalyticsOptions analytics;

  /// Toggles for automatic instrumentation: network requests, launch times,
  /// crash reporting and `debugPrint` capture.
  final InstrumentationOptions instrumentation;

  /// Creates observability options. Every argument is optional; the defaults
  /// enable all telemetry and export to LaunchDarkly.
  const ObservabilityOptions({
    this.isEnabled = true,
    this.serviceName = defaultServiceName,
    this.serviceVersion,
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

/// Not for export: a public `copyWith` could not clear nullable fields
/// without a sentinel design, so the SDK keeps its one copy internal.
extension ObservabilityOptionsCopy on ObservabilityOptions {
  /// A copy with [attributes] replaced (`null` clears them).
  ObservabilityOptions withAttributes(Map<String, Object?>? attributes) =>
      ObservabilityOptions(
        isEnabled: isEnabled,
        serviceName: serviceName,
        serviceVersion: serviceVersion,
        otlpEndpoint: otlpEndpoint,
        backendUrl: backendUrl,
        contextFriendlyName: contextFriendlyName,
        attributes: attributes,
        customHeaders: customHeaders,
        sessionBackgroundTimeout: sessionBackgroundTimeout,
        logsApiLevel: logsApiLevel,
        traces: traces,
        metricsEnabled: metricsEnabled,
        analytics: analytics,
        instrumentation: instrumentation,
      );
}
