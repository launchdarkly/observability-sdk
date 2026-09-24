/// Severity of a log recorded with `LDObserve.recordLog`.
///
/// Each value carries the OpenTelemetry severity number it is exported with.
/// Distinct from `ObservabilityLogLevel`, which is the minimum severity the
/// native SDKs export.
enum LogSeverity {
  trace(1),
  debug(5),
  info(9),
  warn(13),
  error(17),
  fatal(21);

  /// The OpenTelemetry log severity number for this severity.
  final int severityNumber;

  const LogSeverity(this.severityNumber);
}
