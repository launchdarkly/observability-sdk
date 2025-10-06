import 'dart:async';

import 'package:opentelemetry/api.dart' as otel;

import 'api/attribute.dart';
import 'api/span.dart';
import 'api/span_kind.dart';
import 'otel/conversions.dart';
import 'otel/log_convention.dart';
import 'otel/setup.dart';
import 'plugin/observability_plugin.dart';
import 'plugin/observability_config.dart';

const _launchDarklyTracerName = 'launchdarkly-observability';
const _launchDarklyErrorSpanName = 'launchdarkly.error';
const _defaultLogLevel = 'info';

/// Singleton used to access observability features.
final class Observe {
  static bool _shutdown = false;
  static final List<ObservabilityPlugin> _pluginInstances = [];

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

  /// Record an exception with an optional stack trace and attributes.
  ///
  /// In dart the stack trace is independent of the exception object and can
  /// be caught at the same time as an exception.
  /// ```dart
  /// try {
  ///   // thing that throws
  /// catch(err, stack) {
  ///   Observe.record(err, stacktrace: stack);
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
    final combinedAttributes = LogConvention.getEventAttributes(
      message,
      severity,
      stackTrace,
    );
    if (attributes != null) {
      combinedAttributes.addAll(attributes);
    }
    final span = startSpan(LogConvention.spanName);
    span.addEvent(LogConvention.eventName, attributes: combinedAttributes);
    span.end();
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
        Observe.recordLog(line);
      },
    );
  }
}

/// Not for export.
/// Registers a plugin with the singleton and sets up otel.
registerPlugin(
  ObservabilityPlugin plugin,
  String credential,
  ObservabilityConfig config,
) {
  Otel.setup(credential, config);
  Observe._pluginInstances.add(plugin);
}
