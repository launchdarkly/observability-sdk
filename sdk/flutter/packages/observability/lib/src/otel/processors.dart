import 'dart:developer' as developer;

import 'package:opentelemetry/api.dart' as otel;
import 'package:opentelemetry/sdk.dart'
    show ReadOnlySpan, ReadWriteSpan, SpanProcessor;

import '../api/attribute.dart';
import '../api/log_record.dart';
import '../api/log_severity.dart';
import 'conversions.dart';
import 'global_attributes.dart';

const _logName = 'LDObserve';

/// The span processors wired into the tracer provider, in order: global
/// attributes, then the app's [userProcessors] (each guarded so a throw cannot
/// break the pipeline), then the SDK's [exportProcessors].
///
/// Not for export.
List<SpanProcessor> buildSpanProcessors(
  List<SpanProcessor> userProcessors,
  List<SpanProcessor> exportProcessors,
) => [
  GlobalAttributesSpanProcessor(),
  for (final processor in userProcessors) GuardedSpanProcessor(processor),
  ...exportProcessors,
];

/// Fills each starting span with the global attributes it does not already
/// carry. The tracer sets the attributes passed to `startSpan` before any
/// processor runs, so those always win.
///
/// Not for export.
final class GlobalAttributesSpanProcessor implements SpanProcessor {
  @override
  void onStart(ReadWriteSpan span, otel.Context parentContext) {
    final globals = GlobalAttributes.otelAttributes;
    if (globals.isEmpty) {
      return;
    }
    final existing = span.attributes;
    for (final attribute in globals) {
      if (existing.get(attribute.key) == null) {
        span.setAttribute(attribute);
      }
    }
  }

  @override
  void onEnd(ReadOnlySpan span) {}

  @override
  void shutdown() {}

  @override
  void forceFlush() {}
}

/// Runs an app-supplied [SpanProcessor], logging and swallowing anything it
/// throws so the SDK's own processors still run and export.
///
/// Not for export.
final class GuardedSpanProcessor implements SpanProcessor {
  /// Wraps [_delegate].
  GuardedSpanProcessor(this._delegate);

  final SpanProcessor _delegate;
  final _reporter = _FailureReporter();

  @override
  void onStart(ReadWriteSpan span, otel.Context parentContext) {
    try {
      _delegate.onStart(span, parentContext);
    } catch (error, stackTrace) {
      _reporter.report('span processor', _delegate, error, stackTrace);
    }
  }

  @override
  void onEnd(ReadOnlySpan span) {
    try {
      _delegate.onEnd(span);
    } catch (error, stackTrace) {
      _reporter.report('span processor', _delegate, error, stackTrace);
    }
  }

  @override
  void shutdown() {
    try {
      _delegate.shutdown();
    } catch (error, stackTrace) {
      _reporter.report('span processor', _delegate, error, stackTrace);
    }
  }

  @override
  void forceFlush() {
    try {
      _delegate.forceFlush();
    } catch (error, stackTrace) {
      _reporter.report('span processor', _delegate, error, stackTrace);
    }
  }
}

/// A log ready for its recorder, after global attributes and the app's log
/// processors have been applied.
///
/// Not for export.
typedef ProcessedLog = ({
  String message,
  LogSeverity severity,
  StackTrace? stackTrace,
  Map<String, Attribute>? attributes,
});

/// Runs logs through global attributes and then the app's log processors, in
/// order, each guarded so a throw cannot stop the log from being exported.
///
/// Not for export.
final class LogPipeline {
  /// Creates a pipeline running [processors] in order.
  LogPipeline(List<LDLogRecordProcessor> processors)
    : _processors = List.unmodifiable(processors),
      _reporters = [for (final _ in processors) _FailureReporter()];

  final List<LDLogRecordProcessor> _processors;
  final List<_FailureReporter> _reporters;

  /// Applies global attributes, then each processor, to a log.
  ProcessedLog process(
    String message, {
    required LogSeverity severity,
    StackTrace? stackTrace,
    Map<String, Attribute>? attributes,
  }) {
    final withGlobals = GlobalAttributes.fillAttributes(attributes);
    if (_processors.isEmpty) {
      return (
        message: message,
        severity: severity,
        stackTrace: stackTrace,
        attributes: withGlobals,
      );
    }

    final record = LDLogRecord(
      message: message,
      severity: severity,
      stackTrace: stackTrace,
      attributes: propertiesFromAttributes(withGlobals),
    );
    for (var i = 0; i < _processors.length; i++) {
      try {
        _processors[i].onEmit(record);
      } catch (error, stackTrace) {
        _reporters[i].report(
          'log processor',
          _processors[i],
          error,
          stackTrace,
        );
      }
    }
    return (
      message: record.message,
      severity: record.severity,
      stackTrace: record.stackTrace,
      attributes: attributesFromProperties(record.attributes),
    );
  }
}

/// Logs the first failure of one processor; later ones would only repeat it
/// for every span or log.
final class _FailureReporter {
  bool _reported = false;

  void report(
    String kind,
    Object processor,
    Object error,
    StackTrace stackTrace,
  ) {
    if (_reported) {
      return;
    }
    _reported = true;
    developer.log(
      'A custom $kind (${processor.runtimeType}) threw; it is skipped and '
      'telemetry is still exported. Further failures are not logged.',
      name: _logName,
      error: error,
      stackTrace: stackTrace,
    );
  }
}
