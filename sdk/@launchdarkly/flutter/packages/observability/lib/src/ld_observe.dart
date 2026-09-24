// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/api/LDObserve.cs.

import 'dart:async';
import 'dart:ui' show PlatformDispatcher;

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import 'api/log_severity.dart';
import 'api/span.dart';
import 'api/span_kind.dart';
import 'api/span_status_code.dart';
import 'instrumentation/click/click_instrumentation.dart';
import 'observe_otel.dart';
import 'otel/conversions.dart';
import 'options/observability_options.dart';
import 'options/session_replay_options.dart';
import 'platform/ld_observe_platform.dart';
import 'plugin/ld_observe_plugin.dart';

/// Static facade for LaunchDarkly observability and session replay from
/// Flutter. Mirrors the C# `LaunchDarkly.Observability.LDObserve` facade and is
/// the single public entry point for both initialization and recording.
///
/// Fully usable on web and mobile: it only depends on cross-platform code (the
/// platform abstraction and the Dart OpenTelemetry pipeline), never on the
/// native bridge directly.
///
/// Dart does not support method overloading, so the two init variants are split
/// into [LDObserve.init] (LaunchDarkly client backed) and
/// [LDObserve.initStandalone] (standalone, no client) — matching the two
/// `Init(client, ...)` / `Init(mobileKey, ...)` overloads in MAUI.
final class LDObserve {
  LDObserve._();

  /// Boots observability (and optional session replay) by registering the
  /// internal plugin on an already-constructed [LDClient]. Mirrors
  /// `LDObserve.Init(LdClient client, ...)`.
  ///
  /// When [replay] is omitted, session replay is not wired up.
  ///
  /// The returned future completes with `true` once observability is ready.
  /// Recording calls made before then are dropped (the most recent screen
  /// view is the exception: it is replayed once ready), so await it before
  /// recording anything that must not be lost. Awaiting is optional.
  ///
  /// The future never completes with an error. It completes with `false` when
  /// startup fails (the error is logged, and calling [init] again retries) or
  /// after [shutdown]. Only the first successful init takes effect: a later
  /// call is logged and ignored, including its options, and reports the first
  /// call's outcome.
  static Future<bool> init(
    LDClient client, {
    required ObservabilityOptions observability,
    SessionReplayOptions? replay,
  }) {
    final plugin = LDObservePlugin(observability, replay: replay);
    client.registerPlugin(plugin);
    return plugin.bootResult ?? Future.value(false);
  }

  /// Boots observability (and optional session replay) standalone, without a
  /// LaunchDarkly client. Mirrors `LDObserve.Init(string mobileKey, ...)`.
  ///
  /// When [replay] is omitted, session replay is started disabled.
  ///
  /// Completes like [init]: `true` once ready, `false` if startup fails or
  /// after [shutdown], never with an error.
  static Future<bool> initStandalone(
    String mobileKey, {
    required ObservabilityOptions observability,
    SessionReplayOptions? replay,
  }) {
    return LDObservePlugin(observability, replay: replay).boot(mobileKey);
  }

  /// Start a span with the given name and optional [properties].
  ///
  /// The span is the current span, and the parent of spans started after it,
  /// until you call [Span.end]; end nested spans in reverse order. A span that
  /// is never ended is never exported. For work that crosses `await`s, prefer
  /// [withSpan].
  ///
  /// [properties] is a plain Dart map (`Map<String, Object?>`); scalar and
  /// homogeneous-list values are attached to the span as attributes. Values
  /// that cannot be represented as span attributes are ignored.
  static Span startSpan(
    String name, {
    SpanKind kind = SpanKind.internal,
    Map<String, Object?>? properties,
  }) => ObserveOtel.startSpan(
    name,
    kind: kind,
    attributes: attributesFromProperties(properties),
  );

  /// Run [fn] inside a new span that ends automatically.
  ///
  /// The span is current for everything [fn] does, including across `await`s,
  /// so spans started inside it are its children. If [fn] returns a `Future`,
  /// the span ends when that future completes. If [fn] throws or its future
  /// fails, the error is recorded on the span, the span's status is set to
  /// [SpanStatusCode.error], and the error propagates unchanged.
  ///
  /// ```dart
  /// final orders = await LDObserve.withSpan('load-orders', (span) async {
  ///   span.setAttribute('page', 1);
  ///   return api.fetchOrders(page: 1);
  /// });
  /// ```
  static T withSpan<T>(
    String name,
    T Function(Span span) fn, {
    SpanKind kind = SpanKind.internal,
    Map<String, Object?>? properties,
  }) => ObserveOtel.withSpan(
    name,
    fn,
    kind: kind,
    attributes: attributesFromProperties(properties),
  );

  /// Record a custom `track` event as a `track` span.
  ///
  /// Mirrors `LDClient.track(eventName, {data, metricValue})` so the same call
  /// shape works whether the event is recorded through the LaunchDarkly client
  /// (via the `afterTrack` hook) or directly through this API. Use this for
  /// standalone observability (no LaunchDarkly client) or to record custom
  /// events that should not also be sent to LaunchDarkly. [properties] is a
  /// plain JSON map so callers need not depend on `LDValue`; object members are
  /// attached as span attributes.
  static void track(
    String eventName, {
    Map<String, Object?>? properties,
    num? metricValue,
  }) => ObserveOtel.track(
    eventName,
    data: properties == null ? null : LDValue.ofDynamic(properties),
    metricValue: metricValue,
  );

  /// Record a screen view (navigation) so it appears on the Session Replay
  /// timeline as a `Navigate` event and as a `screen_view` span.
  ///
  /// Native automatic screen detection only sees the single host
  /// Activity/UIViewController that Flutter renders into, so Flutter route
  /// changes must be reported explicitly. Call this on navigation (or attach the
  /// provided [LDNavigatorObserver] to your `MaterialApp`/`Navigator`).
  ///
  /// [name] is the human-readable screen/route name. [screenClass], [screenId]
  /// and [category] are optional classifiers, and [properties] is a plain Dart
  /// map of additional attributes attached to the `screen_view` span.
  static void trackScreenView(
    String name, {
    String? screenClass,
    String? screenId,
    String? category,
    Map<String, Object?>? properties,
  }) => ObserveOtel.trackScreenView(
    name,
    screenClass: screenClass,
    screenId: screenId,
    category: category,
    properties: properties,
  );

  /// Record a click so it appears on the Session Replay timeline as a `Click`
  /// event and as a `click` span.
  ///
  /// Taps are captured automatically when the widget tree is wrapped in
  /// `SessionReplayCapture`, which resolves the pressed widget and reports it
  /// through this same path. Use this API for interactions that automatic capture
  /// cannot observe — a gesture inside a custom painter, a hardware button, a
  /// programmatic action you want to appear as a click. Calling it from an
  /// `onPressed` that automatic capture also sees will report the tap twice; to
  /// give an automatically captured widget a stable name, wrap it in [LDClick]
  /// instead.
  ///
  /// [id] is a stable identifier for the element (`event.id`), [tag] its type
  /// (`event.tag`, e.g. `ElevatedButton`), and [text] its visible label
  /// (`event.text`). [x]/[y] are the tap position in Flutter logical pixels —
  /// the units of a gesture's `globalPosition` — and are converted to the units
  /// automatic capture reports on the current platform, so manual and automatic
  /// clicks share one coordinate space. [properties] is a plain Dart map of
  /// additional attributes attached to the `click` span. The screen is filled
  /// in automatically from the most recent [trackScreenView].
  static void trackClick({
    String? id,
    String? tag,
    String? text,
    double? x,
    double? y,
    Map<String, Object?>? properties,
  }) {
    final scale = ClickInstrumentation.platformScale(
      PlatformDispatcher.instance.implicitView?.devicePixelRatio ?? 1.0,
    );
    ObserveOtel.trackClick(
      id: id,
      tag: tag,
      text: text,
      x: x == null ? null : (x * scale).round(),
      y: y == null ? null : (y * scale).round(),
      properties: properties,
    );
  }

  /// Record an exception with an optional stack trace and [properties].
  ///
  /// [properties] is a plain Dart map (`Map<String, Object?>`) of additional
  /// attributes to attach to the error.
  static void recordException(
    dynamic exception, {
    StackTrace? stackTrace,
    Map<String, Object?>? properties,
  }) => ObserveOtel.recordException(
    exception,
    stackTrace: stackTrace,
    attributes: attributesFromProperties(properties),
  );

  /// Record a log with optional [properties]. Defaults [severity] to
  /// [LogSeverity.info].
  ///
  /// [properties] is a plain Dart map (`Map<String, Object?>`) of additional
  /// attributes to attach to the log.
  static void recordLog(
    String message, {
    LogSeverity severity = LogSeverity.info,
    StackTrace? stackTrace,
    Map<String, Object?>? properties,
  }) => ObserveOtel.recordLog(
    message,
    severity: severity,
    stackTrace: stackTrace,
    attributes: attributesFromProperties(properties),
  );

  /// Get a zone specification which intercepts print statements.
  static ZoneSpecification zoneSpecification() =>
      ObserveOtel.zoneSpecification();

  /// Shut down observability and session replay for the rest of the process.
  ///
  /// Flushes buffered spans, removes the Dart instrumentations (lifecycle,
  /// `debugPrint`, click capture), stops native session replay, and makes every
  /// recording API a no-op. Shutdown is terminal: calling [init] or
  /// [initStandalone] afterwards does nothing.
  ///
  /// The returned future completes once native has stopped session replay;
  /// awaiting it is optional. It never completes with an error, and repeated
  /// calls return the same future.
  ///
  /// On iOS and Android the native observability SDK has no teardown, so its
  /// automatic instrumentation (crash reporting, network requests, launch
  /// times, native lifecycle spans) keeps running until the process exits.
  static Future<void> shutdown() => ObserveOtel.shutdown();

  /// The native observability bridge version reported during startup, or an
  /// empty string before initialization (or on web, which has no native
  /// bridge).
  static String get nativeVersion => LDObservePlatform.instance.nativeVersion;
}
