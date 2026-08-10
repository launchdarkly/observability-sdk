// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/api/LDObserve.cs.

import 'dart:async';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import 'api/span.dart';
import 'api/span_kind.dart';
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
  static void init(
    LDClient client, {
    required ObservabilityOptions observability,
    SessionReplayOptions? replay,
  }) {
    client.registerPlugin(LDObservePlugin(observability, replay: replay));
  }

  /// Boots observability (and optional session replay) standalone, without a
  /// LaunchDarkly client. Mirrors `LDObserve.Init(string mobileKey, ...)`.
  ///
  /// When [replay] is omitted, session replay is started disabled.
  static Future<void> initStandalone(
    String mobileKey, {
    required ObservabilityOptions observability,
    SessionReplayOptions? replay,
  }) {
    return LDObservePlugin(observability, replay: replay).boot(mobileKey);
  }

  /// Start a span with the given name and optional [properties].
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

  /// Record a log with optional [properties]. Defaults [severity] to `info`.
  ///
  /// [properties] is a plain Dart map (`Map<String, Object?>`) of additional
  /// attributes to attach to the log.
  static void recordLog(
    String message, {
    String severity = 'info',
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

  /// Shutdown observability. Once shutdown observability cannot be restarted.
  static void shutdown() => ObserveOtel.shutdown();

  /// The native observability bridge version reported during startup, or an
  /// empty string before initialization (or on web, which has no native
  /// bridge).
  static String get nativeVersion => LDObservePlatform.instance.nativeVersion;
}
