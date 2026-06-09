// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/api/LDObserve.cs.

import 'dart:async';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import 'api/attribute.dart';
import 'api/span.dart';
import 'api/span_kind.dart';
import 'observe_otel.dart';
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

  /// Start a span with the given name and optional attributes.
  static Span startSpan(
    String name, {
    SpanKind kind = SpanKind.internal,
    Map<String, Attribute>? attributes,
  }) => ObserveOtel.startSpan(name, kind: kind, attributes: attributes);

  /// Record a custom `track` event as a `track` span.
  ///
  /// Mirrors `LDClient.track(eventName, {data, metricValue})` so the same call
  /// shape works whether the event is recorded through the LaunchDarkly client
  /// (via the `afterTrack` hook) or directly through this API. Use this for
  /// standalone observability (no LaunchDarkly client) or to record custom
  /// events that should not also be sent to LaunchDarkly. `data` is a plain JSON
  /// map so callers need not depend on `LDValue`; object members are attached as
  /// span attributes.
  static void track(
    String eventName, {
    Map<String, dynamic>? data,
    num? metricValue,
  }) => ObserveOtel.track(
    eventName,
    data: data == null ? null : LDValue.ofDynamic(data),
    metricValue: metricValue,
  );

  /// Record an exception with an optional stack trace and attributes.
  static void recordException(
    dynamic exception, {
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  }) => ObserveOtel.recordException(
    exception,
    stackTrace: stackTrace,
    attributes: attributes,
  );

  /// Record a log with optional attributes. Defaults [severity] to `info`.
  static void recordLog(
    String message, {
    String severity = 'info',
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  }) => ObserveOtel.recordLog(
    message,
    severity: severity,
    stackTrace: stackTrace,
    attributes: attributes,
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
