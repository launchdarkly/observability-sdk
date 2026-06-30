// Fallback used when neither `dart:io` nor `dart:js_interop` is available.
// There is no native bridge on such targets, so the cross-platform Dart
// OpenTelemetry pipeline is used: spans over OTLP/HTTP and logs as span events.

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:opentelemetry/api.dart' as otel;
import 'package:opentelemetry/sdk.dart'
    show BatchSpanProcessor, CollectorExporter, SpanProcessor;

import '../../api/attribute.dart';
import '../../plugin/observability_config.dart';
import '../conversions.dart';
import '../log_convention.dart';
import '../screen_view_convention.dart';
import '../track_convention.dart';
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

  @override
  TrackRecorder createTrackRecorder(ObservabilityConfig config) =>
      _SpanTrackRecorder(config.trackEventsEnabled);

  @override
  IdentifyRecorder createIdentifyRecorder(ObservabilityConfig config) =>
      _NoopIdentifyRecorder();

  @override
  ScreenViewRecorder createScreenViewRecorder(ObservabilityConfig config) =>
      _SpanScreenViewRecorder(config.pageViewsEnabled);
}

/// Emits each screen view as a Dart `screen_view` span via the OpenTelemetry
/// pipeline. Gated by `analytics.pageViews`.
class _SpanScreenViewRecorder implements ScreenViewRecorder {
  _SpanScreenViewRecorder(this._pageViewsEnabled);

  final bool _pageViewsEnabled;

  @override
  void trackScreenView(
    String name, {
    String? screenClass,
    String? screenId,
    String? category,
    Map<String, Object?>? properties,
  }) {
    if (!_pageViewsEnabled) {
      return;
    }
    final tracer = otel.globalTracerProvider.getTracer(_tracerName);
    final span = tracer.startSpan(
      ScreenViewConvention.spanName,
      attributes: convertAttributes(
        ScreenViewConvention.getSpanAttributes(
          name: name,
          screenClass: screenClass,
          screenId: screenId,
          category: category,
          properties: properties,
        ),
      ),
    );
    span.setStatus(otel.StatusCode.ok);
    span.end();
  }
}

/// No Session Replay or context-key caching exists on the Dart pipeline, so
/// `identify` has nothing to forward.
class _NoopIdentifyRecorder implements IdentifyRecorder {
  @override
  void identify({
    required Map<String, String> contextKeys,
    required String canonicalKey,
    required bool completed,
  }) {}
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
