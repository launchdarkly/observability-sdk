import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:opentelemetry/api.dart' as otel;
import 'api/span.dart';
import 'api/span_kind.dart';
import 'otel/conversions.dart';

const _launchDarklyTracerName = 'launchdarkly-observability';

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
}
