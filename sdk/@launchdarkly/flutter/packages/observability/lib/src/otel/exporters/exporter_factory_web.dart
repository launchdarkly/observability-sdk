// Web (and any non-io target) exporter construction. Uses the Dart
// OpenTelemetry pipeline directly: spans are exported over OTLP/HTTP via
// [CollectorExporter], and logs are emitted as span events (the Dart pipeline
// has no standalone logs exporter). This preserves the pre-existing behaviour.

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:opentelemetry/api.dart' as otel;
import 'package:opentelemetry/sdk.dart'
    show BatchSpanProcessor, CollectorExporter, SpanProcessor;

import '../../api/attribute.dart';
import '../../plugin/observability_config.dart';
import '../conversions.dart';
import '../log_convention.dart';
import '../track_convention.dart';
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

  @override
  TrackRecorder createTrackRecorder(ObservabilityConfig config) =>
      _SpanTrackRecorder(config.trackEventsEnabled);
}

/// Emits each `track` event as a Dart `track` span via the OpenTelemetry
/// pipeline. Gated by `analytics.trackEvents`.
class _SpanTrackRecorder implements TrackRecorder {
  _SpanTrackRecorder(this._trackEventsEnabled);

  final bool _trackEventsEnabled;

  @override
  void track(
    String eventName, {
    LDValue? data,
    num? metricValue,
    LDContext? context,
  }) {
    if (!_trackEventsEnabled) {
      return;
    }
    final tracer = otel.globalTracerProvider.getTracer(_tracerName);
    final span = tracer.startSpan(
      TrackConvention.spanName,
      attributes: convertAttributes(
        TrackConvention.getSpanAttributes(
          eventName: eventName,
          data: data,
          metricValue: metricValue,
          context: context,
        ),
      ),
    );
    span.setStatus(otel.StatusCode.ok);
    span.end();
  }
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
