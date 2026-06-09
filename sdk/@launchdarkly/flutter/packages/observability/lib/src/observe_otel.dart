import 'dart:async';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:opentelemetry/api.dart' as otel;

import 'api/attribute.dart';
import 'api/span.dart';
import 'api/span_kind.dart';
import 'api/span_status_code.dart';
import 'otel/conversions.dart';
import 'otel/setup.dart';
import 'otel/track_convention.dart';
import 'plugin/ld_observe_plugin.dart';
import 'plugin/observability_config.dart';

const _launchDarklyTracerName = 'launchdarkly-observability';
const _launchDarklyErrorSpanName = 'launchdarkly.error';
const _defaultLogLevel = 'info';

/// Internal implementation of the observability recording APIs.
///
/// Not for export: customer code reaches these through the public `LDObserve`
/// facade, which delegates to this class. Kept platform-agnostic so it works on
/// both mobile and web.
final class ObserveOtel {
  static bool _shutdown = false;
  static final List<LDObservePlugin> _pluginInstances = [];

  /// Whether `track` spans are emitted. Mirrors `analytics.trackEvents`; set
  /// during plugin registration. Defaults to `true` so manual track calls made
  /// before registration completes are not dropped.
  static bool _trackEventsEnabled = true;

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

  /// Emit a `track` span for a custom event.
  ///
  /// The single emitter for both track paths: the LaunchDarkly client's
  /// `afterTrack` hook (which supplies the evaluation [context]) and the manual
  /// [LDObserve.track] API (which has no context). Gated by
  /// `analytics.trackEvents`; mirrors the native iOS/Android track span.
  static void track(
    String eventName, {
    LDValue? data,
    num? metricValue,
    LDContext? context,
  }) {
    if (!_trackEventsEnabled) {
      return;
    }
    final span = startSpan(
      TrackConvention.spanName,
      attributes: TrackConvention.getSpanAttributes(
        eventName: eventName,
        data: data,
        metricValue: metricValue,
        context: context,
      ),
    );
    span.setStatus(SpanStatusCode.ok);
    span.end();
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
    span.recordException(
      exception,
      attributes: attributes,
      stackTrace: stackTrace ?? StackTrace.empty,
    );
    span.end();
  }

  /// Record a log with optional attributes.
  ///
  /// If [severity] is not provided, then it will default to 'info'.
  /// An optional [stackTrace] can be provided.
  ///
  /// The `StackTrace.current` property can be used to capture a stack trace.
  static void recordLog(
    String message, {
    String severity = _defaultLogLevel,
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

  /// Shutdown observability. Once shutdown observability cannot be restarted.
  static void shutdown() {
    if (!_shutdown) {
      Otel.shutdown();
      for (final plugin in _pluginInstances) {
        plugin.dispose();
      }
      _shutdown = true;
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
/// Registers a plugin with the singleton and sets up otel.
void registerPlugin(
  LDObservePlugin plugin,
  String credential,
  ObservabilityConfig config,
) {
  Otel.setup(credential, config);
  ObserveOtel._trackEventsEnabled = config.trackEventsEnabled;
  ObserveOtel._pluginInstances.add(plugin);
}
