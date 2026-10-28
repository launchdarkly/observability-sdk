import 'log_severity.dart';

/// A log on its way to export, as seen by an [LDLogRecordProcessor].
///
/// Every field is mutable: a processor's changes are what gets exported.
/// [attributes] already holds the global attributes from
/// `LDObserve.setAttributes`, beneath the attributes passed to the call.
final class LDLogRecord {
  /// The log message.
  String message;

  /// The log severity.
  LogSeverity severity;

  /// The stack trace attached to the log, if any.
  StackTrace? stackTrace;

  /// The log attributes as plain Dart values. Values that cannot be
  /// represented as an attribute (such as nested maps) are dropped on export.
  final Map<String, Object?> attributes;

  /// Creates a log record. The SDK creates one per log; construct your own to
  /// unit test a processor.
  LDLogRecord({
    required this.message,
    this.severity = LogSeverity.info,
    this.stackTrace,
    Map<String, Object?>? attributes,
  }) : attributes = {...?attributes};
}

/// Observes, and may modify, every log recorded through the SDK before it is
/// exported. Pass processors through `ObservabilityOptions.logProcessors`.
///
/// Processors run in order on the thread that recorded the log, so keep them
/// fast. A processor that throws is skipped for that log; the log is still
/// exported.
abstract class LDLogRecordProcessor {
  /// Const constructor for subclasses.
  const LDLogRecordProcessor();

  /// Called for each log before it is exported.
  void onEmit(LDLogRecord record);
}
