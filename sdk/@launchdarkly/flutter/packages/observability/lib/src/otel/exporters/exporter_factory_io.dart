// Native (iOS/Android) exporter construction. Forwards Dart-recorded spans and
// logs over the pigeon bridge to the native SDK, which re-creates them so the
// native pipeline stamps `session.id` (and applies sampling/batching). This
// mirrors MAUI's `LDTraceExporter` + `TraceBuilderAdapter` and the direct
// native `RecordLog` call in `ObservabilityService.cs`.

import 'dart:async';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:opentelemetry/api.dart' as otel;
import 'package:opentelemetry/sdk.dart'
    show BatchSpanProcessor, ReadOnlySpan, SpanExporter, SpanProcessor;

import '../../api/attribute.dart';
import '../../plugin/observability_config.dart';
import '../../platform/io/messages.g.dart' as wire;
import '../conversions.dart';
import 'exporter_factory.dart';

ObservabilityExporters createObservabilityExporters() => _IoExporters();

class _IoExporters implements ObservabilityExporters {
  final wire.LDNativeApi _api = wire.LDNativeApi();

  @override
  List<SpanProcessor> createSpanProcessors(ObservabilityConfig config) => [
    BatchSpanProcessor(_NativeSpanExporter(_api)),
  ];

  @override
  LogRecorder createLogRecorder(ObservabilityConfig config) =>
      _NativeLogRecorder(_api);

  @override
  TrackRecorder createTrackRecorder(ObservabilityConfig config) =>
      _NativeTrackRecorder(_api);
}

/// Forwards each `track` call to the native observability SDK so it emits the
/// native `track` span (gated natively by `analytics.trackEvents`) and the
/// Session Replay `Track` timeline event (always). The Dart span pipeline is
/// intentionally bypassed for track on mobile so the event is not emitted twice.
class _NativeTrackRecorder implements TrackRecorder {
  _NativeTrackRecorder(this._api);

  final wire.LDNativeApi _api;

  @override
  void track(
    String eventName, {
    LDValue? data,
    num? metricValue,
    LDContext? context,
  }) {
    // Reuse the SDK's canonical `LDValue` -> JSON transformation. Only object
    // payloads carry `track` attributes natively, so non-object payloads map to
    // `null`.
    final dynamic json = data?.toDynamic();
    // Forward the evaluation context's kind -> key pairs so the native `track`
    // span is attributed to the same context the web SDK records (the native
    // LaunchDarkly client only holds an anonymous bootstrap context). `null` for
    // the manual `LDObserve.track` path, which has no context.
    final Map<String, String>? contextKeys = (context != null && context.valid)
        ? context.keys
        : null;
    unawaited(
      _api.track(
        eventName,
        json is Map ? Map<String, Object?>.from(json) : null,
        metricValue?.toDouble(),
        contextKeys,
      ),
    );
  }
}

/// Converts each completed Dart span into the pigeon wire type and forwards it
/// to the native tracer. Export is fire-and-forget: the native side re-creates
/// and exports the span asynchronously.
class _NativeSpanExporter implements SpanExporter {
  _NativeSpanExporter(this._api);

  final wire.LDNativeApi _api;
  var _shutdown = false;

  @override
  void export(List<ReadOnlySpan> spans) {
    if (_shutdown || spans.isEmpty) return;
    final payload = spans.map(_toWire).toList();
    unawaited(_api.exportSpans(payload));
  }

  @override
  void forceFlush() {}

  @override
  void shutdown() {
    _shutdown = true;
  }

  wire.LDSpanData _toWire(ReadOnlySpan span) {
    final startSeconds = span.startTime.toDouble() / 1e9;
    final endNanos = span.endTime;
    final endSeconds = endNanos != null
        ? endNanos.toDouble() / 1e9
        : startSeconds;

    final attributes = <String, Object?>{};
    for (final key in span.attributes.keys) {
      attributes[key] = span.attributes.get(key);
    }

    final events = span.events
        .map(
          (e) => wire.LDSpanEvent(
            name: e.name,
            attributes: {for (final a in e.attributes) a.key: a.value},
          ),
        )
        .toList();

    return wire.LDSpanData(
      name: span.name,
      startTimeSeconds: startSeconds,
      endTimeSeconds: endSeconds,
      traceId: span.spanContext.traceId.toString(),
      spanId: span.spanContext.spanId.toString(),
      parentSpanId: _parentSpanId(span.parentSpanId),
      attributes: attributes,
      events: events,
      statusCode: _statusCode(span.status.code),
    );
  }

  /// Normalizes empty / all-zero parent ids to an empty string so the native
  /// side treats the span as a root.
  static String _parentSpanId(otel.SpanId id) {
    final hex = id.toString();
    if (hex.isEmpty || RegExp(r'^0+$').hasMatch(hex)) return '';
    return hex;
  }

  /// Maps the OTel status to the native code (0 = unset, 1 = ok, 2 = error).
  static int _statusCode(otel.StatusCode code) {
    switch (code) {
      case otel.StatusCode.ok:
        return 1;
      case otel.StatusCode.error:
        return 2;
      case otel.StatusCode.unset:
        return 0;
    }
  }
}

/// Forwards logs to the native logger so each is emitted as a real native
/// `LogRecord` with `session.id` and trace/span correlation.
class _NativeLogRecorder implements LogRecorder {
  _NativeLogRecorder(this._api);

  final wire.LDNativeApi _api;

  @override
  void recordLog(
    String message, {
    required String severity,
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  }) {
    final attrs = <String, Object?>{};
    attributes?.forEach((key, attribute) {
      final converted = convertAttribute(key, attribute);
      if (converted != null) {
        attrs[key] = converted.value;
      }
    });
    if (stackTrace != null) {
      attrs['code.stacktrace'] = stackTrace.toString();
    }

    final spanContext = otel.spanContextFromContext(otel.Context.current);
    final hasContext = spanContext.isValid;

    unawaited(
      _api.recordLog(
        wire.LDLogRecord(
          message: message,
          severityNumber: _severityNumber(severity),
          traceId: hasContext ? spanContext.traceId.toString() : null,
          spanId: hasContext ? spanContext.spanId.toString() : null,
          attributes: attrs,
        ),
      ),
    );
  }

  /// Maps a textual severity to the OpenTelemetry severity number. Defaults to
  /// INFO (9) for unknown values.
  static int _severityNumber(String severity) {
    switch (severity.toLowerCase()) {
      case 'trace':
        return 1;
      case 'debug':
        return 5;
      case 'info':
        return 9;
      case 'warn':
      case 'warning':
        return 13;
      case 'error':
        return 17;
      case 'fatal':
        return 21;
      default:
        return 9;
    }
  }
}
