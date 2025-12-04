/// The status of the span.
enum SpanStatusCode {
  /// The status has not been set. This is the default status.
  unset,

  /// The operation the span represents encountered an error.
  error,

  /// The operation the span represents completed successfully.
  ok,
}
