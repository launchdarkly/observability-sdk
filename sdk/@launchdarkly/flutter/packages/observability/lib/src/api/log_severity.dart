/// Severity of a log recorded with `LDObserve.recordLog`.
///
/// Each value carries the OpenTelemetry severity number it is exported with.
/// Distinct from `ObservabilityLogLevel`, which is the minimum severity the
/// native SDKs export.
enum LogSeverity {
  /// Fine-grained diagnostic detail (OTel `TRACE`, 1).
  trace(1),

  /// Debugging information (OTel `DEBUG`, 5).
  debug(5),

  /// Normal operational messages (OTel `INFO`, 9).
  info(9),

  /// Something unexpected that the app recovered from (OTel `WARN`, 13).
  warn(13),

  /// A failed operation (OTel `ERROR`, 17).
  error(17),

  /// An error the app cannot recover from (OTel `FATAL`, 21).
  fatal(21);

  /// The OpenTelemetry log severity number for this severity.
  final int severityNumber;

  const LogSeverity(this.severityNumber);
}
