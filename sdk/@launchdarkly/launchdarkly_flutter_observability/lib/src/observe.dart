import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:opentelemetry/api.dart' as otel;
import 'api/span.dart';
import 'api/span_kind.dart';
import 'otel/conversions.dart';

const _launchDarklyTracerName = 'launchdarkly-observability';
const _launchDarklyErrorSpanName = 'launchdarkly.error';

/// Singleton used to access observability features.
final class Observe {
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
}
