/// The kind of the span.
enum SpanKind {
  /// Default value. Indicates that the span represents an internal operation
  /// within an application, as opposed to an operations with remote parents or
  /// children.
  internal,

  /// Indicates that the span describes a request to a remote service where the
  /// client awaits a response.
  client,

  /// Indicates that the span covers server-side handling of a remote request
  /// while the client awaits a response.
  server,

  ///  Indicates that the span describes the initiation or scheduling of a local
  ///  or remote operation.
  producer,

  /// Indicates that the span represents the processing of an operation
  /// initiated by a producer, where the producer does not wait for the outcome.
  consumer,
}
