// Fallback used when neither `dart:io` nor `dart:js_interop` is available.
// There is no native bridge on such targets, so the cross-platform Dart
// OpenTelemetry pipeline is used: spans over OTLP/HTTP and logs as span events.

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

ObservabilityExporters createObservabilityExporters() => _StubExporters();

class _StubExporters implements ObservabilityExporters {
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
