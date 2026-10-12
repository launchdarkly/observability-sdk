import 'dart:async';
import 'dart:developer' as developer;
import 'package:flutter/foundation.dart' show visibleForTesting;

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:opentelemetry/api.dart' as otel;

import 'api/attribute.dart';
import 'api/log_severity.dart';
import 'api/span.dart';
import 'api/span_kind.dart';
import 'api/span_status_code.dart';
import 'otel/conversions.dart';
import 'otel/setup.dart';
import 'platform/ld_observe_platform.dart';
import 'plugin/ld_observe_plugin.dart';
import 'plugin/observability_config.dart';

const _launchDarklyTracerName = 'launchdarkly-observability';
const _launchDarklyErrorSpanName = 'launchdarkly.error';

/// Internal implementation of the observability recording APIs.
///
/// Not for export: customer code reaches these through the public `LDObserve`
/// facade, which delegates to this class. Kept platform-agnostic so it works on
/// both mobile and web.
final class ObserveOtel {
  static bool _shutdown = false;
  static final List<LDObservePlugin> _pluginInstances = [];

  /// Start a span with the given name and optional attributes.
  static Span startSpan(
    String name, {
    SpanKind kind = SpanKind.internal,
    Map<String, Attribute>? attributes,
  }) {
    final tracer = otel.globalTracerProvider.getTracer(_launchDarklyTracerName);
    final span = tracer.startSpan(
      name,
      kind: convertKind(kind),
      attributes: convertAttributes(attributes),
    );
    final token = otel.Context.attach(
      otel.contextWithSpan(otel.Context.current, span),
    );

    return wrapSpan(span, token);
  }

  /// Runs [fn] inside a new span and ends the span when [fn] completes.
  ///
  /// The span is current for everything [fn] runs, including across `await`s,
  /// because it is scoped to a zone rather than a global stack. When [fn]
  /// returns a `Future` the span ends once it completes. A thrown error or a
  /// failed future is recorded on the span, which is marked as an error, and
  /// then propagated unchanged.
  static T withSpan<T>(
    String name,
    T Function(Span span) fn, {
    SpanKind kind = SpanKind.internal,
    Map<String, Attribute>? attributes,
  }) {
    final tracer = otel.globalTracerProvider.getTracer(_launchDarklyTracerName);
    final otelSpan = tracer.startSpan(
      name,
      kind: convertKind(kind),
      attributes: convertAttributes(attributes),
    );
    final span = wrapSpan(otelSpan, null);

    late final T result;
    try {
      otel.zone(otel.contextWithSpan(otel.Context.current, otelSpan)).run(() {
        result = fn(span);
        // The zone keeps the span current until the future it is handed
        // completes, and chains `whenComplete` onto it, which would surface a
        // failure as an unhandled error. Hand it a future that cannot fail;
        // the caller still gets the original.
        final Object? pending = result;
        return pending is Future ? pending.then((_) {}, onError: (_) {}) : null;
      });
    } catch (error, stackTrace) {
      _failSpan(span, error, stackTrace);
      rethrow;
    }
    final Object? outcome = result;
    if (outcome is Future) {
      unawaited(
        outcome.then(
          (_) => span.end(),
          onError: (Object error, StackTrace stackTrace) =>
              _failSpan(span, error, stackTrace),
        ),
      );
    } else {
      span.end();
    }
    return result;
  }

  static void _failSpan(Span span, Object error, StackTrace stackTrace) {
    spanRecordException(span, error, stackTrace: stackTrace);
    span.setStatus(SpanStatusCode.error);
    span.end();
  }

  /// Record a `track` event for a custom event.
  ///
  /// The single entry point for both track paths: the LaunchDarkly client's
  /// `afterTrack` hook (which supplies the evaluation [context]) and the manual
  /// [LDObserve.track] API (which has no context). Delegates to the
  /// platform-appropriate recorder: on web a Dart `track` span is emitted; on
  /// mobile the native observability SDK is invoked so it emits the native
  /// `track` span (gated by `analytics.trackEvents`) and the Session Replay
  /// `Track` timeline event. `null` before the pipeline is initialized, in which
  /// case the event is dropped.
  static void track(
    String eventName, {
    LDValue? data,
    num? metricValue,
    LDContext? context,
  }) {
    Otel.trackRecorder?.track(
      eventName,
      data: data,
      metricValue: metricValue,
      context: context,
    );
  }

  /// Record a screen view through the platform-appropriate pipeline.
  ///
  /// On mobile the native observability SDK is invoked so it emits the native
  /// `screen_view` span and the Session Replay `Navigate` timeline event (native
  /// automatic screen detection never sees Flutter route changes). On web a Dart
  /// `screen_view` span is emitted, gated by `analytics.views`. A screen view
  /// recorded before the pipeline is initialized is held and replayed once it
  /// is, so the screen a session opens on is not lost to the asynchronous boot.
  static void trackScreenView(
    String name, {
    String? screenClass,
    String? screenId,
    String? category,
    Map<String, Object?>? properties,
  }) {
    Otel.recordScreenView(
      (recorder) => recorder.trackScreenView(
        name,
        screenClass: screenClass,
        screenId: screenId,
        category: category,
        properties: properties,
      ),
    );
  }

  /// Record a click through the platform-appropriate pipeline.
  ///
  /// The single entry point for both click paths: automatic detection (which
  /// resolves the tapped widget in Dart) and the manual [LDObserve.trackClick]
  /// API. On mobile the native observability SDK is invoked so it emits the
  /// native `click` span and the Session Replay `Click` timeline event; a native
  /// hit-test only ever finds the single Flutter view, so this is the only path
  /// that can describe a Flutter tap. On web a Dart `click` span is emitted,
  /// gated by `analytics.taps`. Dropped before the pipeline is initialized —
  /// unlike the opening screen view, a click before boot has no lasting meaning.
  static void trackClick({
    String? id,
    String? tag,
    String? classname,
    String? text,
    String? xpath,
    int? x,
    int? y,
    int? timestampMillis,
    Map<String, Object?>? properties,
  }) {
    Otel.clickRecorder?.trackClick(
      id: id,
      tag: tag,
      classname: classname,
      text: text,
      xpath: xpath,
      x: x,
      y: y,
      timestampMillis: timestampMillis,
      properties: properties,
    );
  }

  /// Forward an `identify` to the platform-appropriate pipeline.
  ///
  /// Invoked from the LaunchDarkly client's `afterIdentify` hook. On mobile the
  /// native observability SDK caches [contextKeys] so the manual
  /// [LDObserve.track] path is attributed to the active context, and Session
  /// Replay records who the user is on the active recording. On web this is a
  /// no-op. [canonicalKey] is the context's fully-qualified key; [completed]
  /// indicates whether the identify finished successfully (native ignores
  /// incomplete identifies). `null` before the pipeline is initialized, in which
  /// case the identify is dropped.
  static void identify({
    required Map<String, String> contextKeys,
    required String canonicalKey,
    required bool completed,
  }) {
    Otel.identifyRecorder?.identify(
      contextKeys: contextKeys,
      canonicalKey: canonicalKey,
      completed: completed,
    );
  }

  /// Record an exception with an optional stack trace and attributes.
  ///
  /// In dart the stack trace is independent of the exception object and can
  /// be caught at the same time as an exception.
  /// ```dart
  /// try {
  ///   // thing that throws
  /// catch(err, stack) {
  ///   LDObserve.recordException(err, stackTrace: stack);
  /// }
  /// ```
  ///
  /// In order to capture a stack trace that isn't at the origin of the catch
  /// the `StackTrace.current` method can be used.
  ///
  /// The value of the [exception] object will be incorporated into traces
  /// using its `toString` method.
  static void recordException(
    dynamic exception, {
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  }) {
    // The OTEL library currently doesn't have a way to differentiate if there
    // is an active span or not. So currently we always create a span for
    // exceptions.
    final span = startSpan(_launchDarklyErrorSpanName);
    spanRecordException(
      span,
      exception,
      attributes: attributes,
      stackTrace: stackTrace ?? StackTrace.empty,
    );
    span.end();
  }

  /// Record a log with optional attributes.
  ///
  /// If [severity] is not provided, then it will default to
  /// [LogSeverity.info]. An optional [stackTrace] can be provided.
  ///
  /// The `StackTrace.current` property can be used to capture a stack trace.
  static void recordLog(
    String message, {
    LogSeverity severity = LogSeverity.info,
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  }) {
    // Delegate to the platform-appropriate recorder: native log records on
    // mobile (stamped with `session.id`), span events on web. `null` before
    // the pipeline is initialized, in which case the log is dropped.
    Otel.logRecorder?.recordLog(
      message,
      severity: severity,
      stackTrace: stackTrace,
      attributes: attributes,
    );
  }

  /// Whether [shutdown] has run. Terminal: a later boot does nothing.
  static bool get isShutdown => _shutdown;

  /// Shut down observability. Terminal: observability cannot be restarted in
  /// this process, and a later `LDObserve.init` does nothing.
  ///
  /// Flushes buffered Dart spans, removes the Dart instrumentations, and stops
  /// native Session Replay. Native automatic instrumentation keeps running,
  /// because the native observability SDKs have no teardown.
  ///
  /// Every call returns the same future, which completes once native has
  /// acknowledged the stop. It never completes with an error; a failure to
  /// reach native is logged.
  static Future<void> shutdown() => _shutdownResult ??= _shutdownOnce();

  static Future<void>? _shutdownResult;

  /// How long [shutdown] waits for native to confirm Session Replay stopped.
  /// A host that never replies must not leave the shutdown future pending.
  @visibleForTesting
  static Duration nativeShutdownTimeout = const Duration(seconds: 5);

  static Future<void> _shutdownOnce() async {
    _shutdown = true;
    // Plugins first: disposing click capture hands tap reporting back to native
    // through the click recorder, which Otel.shutdown clears.
    for (final plugin in _pluginInstances) {
      plugin.dispose();
    }
    _pluginInstances.clear();
    Otel.shutdown();
    try {
      await LDObservePlatform.instance.shutdown().timeout(
        nativeShutdownTimeout,
      );
    } catch (error, stackTrace) {
      developer.log(
        'LDObserve could not stop session replay during shutdown.',
        name: 'LDObserve',
        error: error,
        stackTrace: stackTrace,
      );
    }
  }

  /// Get a zone specification which intercepts print statements.
  static ZoneSpecification zoneSpecification() {
    return ZoneSpecification(
      print: (Zone self, ZoneDelegate parent, Zone zone, String line) {
        parent.print(zone, line);
        ObserveOtel.recordLog(line);
      },
    );
  }
}

/// Not for export.
/// Registers a plugin with the singleton and sets up otel. Does nothing after
/// [ObserveOtel.shutdown].
void registerPlugin(
  LDObservePlugin plugin,
  String credential,
  ObservabilityConfig config, {
  bool replayEnabled = false,
}) {
  if (ObserveOtel._shutdown) {
    return;
  }
  Otel.setup(credential, config, replayEnabled: replayEnabled);
  ObserveOtel._pluginInstances.add(plugin);
}
