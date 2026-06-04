// Web (and any non-io target) exporter construction. Uses the Dart
// OpenTelemetry pipeline directly: spans are exported over OTLP/HTTP via
// [CollectorExporter], and logs are emitted as span events (the Dart pipeline
// has no standalone logs exporter). This preserves the pre-existing behaviour.

import 'package:opentelemetry/api.dart' as otel;
import 'package:opentelemetry/sdk.dart'
    show BatchSpanProcessor, CollectorExporter, SpanProcessor;

import '../../api/attribute.dart';
import '../../plugin/observability_config.dart';
import '../conversions.dart';
import '../log_convention.dart';
import 'exporter_factory.dart';

const _tracesSuffix = '/v1/traces';
const _tracerName = 'launchdarkly-observability';

ObservabilityExporters createObservabilityExporters() => _WebExporters();

class _WebExporters implements ObservabilityExporters {
  @override
  List<SpanProcessor> createSpanProcessors(ObservabilityConfig config) => [
    BatchSpanProcessor(
      CollectorExporter(Uri.parse('${config.otlpEndpoint}$_tracesSuffix')),
    ),
  ];

  @override
  LogRecorder createLogRecorder(ObservabilityConfig config) =>
      _SpanEventLogRecorder();
}

/// Emits each log as an event on a short-lived span, parented to the active
/// span so it is correlated with the surrounding trace.
class _SpanEventLogRecorder implements LogRecorder {
  @override
  void recordLog(
    String message, {
    required String severity,
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
    final tracer = otel.globalTracerProvider.getTracer(_tracerName);
    final span = tracer.startSpan(LogConvention.spanName);
    span.addEvent(
      LogConvention.eventName,
      attributes: convertAttributes(combinedAttributes),
    );
    span.end();
  }
}
