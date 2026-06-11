import 'package:opentelemetry/api.dart' as otel;

import '../otel/conversions.dart';
import 'attribute.dart';
import 'span_status_code.dart';

/// Represents a single operation within a trace.
final class Span {
  final otel.Span _innerSpan;

  // The context token type is not exported from the opentelemetry package.
  final dynamic _contextToken;

  Span._internal(this._innerSpan, this._contextToken);

  void end() {
    otel.Context.detach(_contextToken);
    _innerSpan.end();
  }

  /// Set an attribute on the span.
  ///
  /// The value may be a boolean, integer, double, string, or a homogeneous
  /// list of any of those. Values that cannot be represented as a span
  /// attribute (such as nested maps or mixed lists) are ignored.
  ///
  /// ```dart
  /// span.setAttribute('my-integer', 42);
  /// span.setAttribute('my-string', 'test');
  /// span.setAttribute('my-list', <double>[3.14, 6.28]);
  /// ```
  void setAttribute(String name, Object? value) {
    final otelAttribute = convertAttribute(name, attributeFromNative(value));
    if (otelAttribute != null) {
      _innerSpan.setAttribute(otelAttribute);
    }
  }

  /// Set attributes on the span.
  /// For details about attributes refer to [setAttribute].
  void setAttributes(Map<String, Object?> attributes) {
    _innerSpan.setAttributes(
      convertAttributes(attributesFromProperties(attributes)),
    );
  }

  /// Record information about an exception that happened during this span.
  void recordException(
    dynamic exception, {
    StackTrace stackTrace = StackTrace.empty,
    Map<String, Object?>? attributes,
  }) {
    // The otel library supports an "escaped" attribute, but attribute is
    // deprecated and no longer recommended, so we aren't exporting it.
    _innerSpan.recordException(
      exception,
      stackTrace: stackTrace,
      attributes: convertAttributes(attributesFromProperties(attributes)),
    );
  }

  /// Add an event to the span with the given attributes.
  void addEvent(String name, {Map<String, Object?>? attributes}) {
    _innerSpan.addEvent(
      name,
      attributes: convertAttributes(attributesFromProperties(attributes)),
    );
  }

  void setStatus(SpanStatusCode status) {
    _innerSpan.setStatus(convertSpanStatus(status));
  }
}

/// Wrap a span with a LaunchDarkly specific API type.
///
/// Not for export.
Span wrapSpan(otel.Span span, dynamic token) {
  return Span._internal(span, token);
}

/// Add an event built from already-typed internal [Attribute] values.
///
/// The public [Span.addEvent] takes native values and converts them, so
/// internal convention code (which already produces `Attribute` maps) must use
/// this entry point to avoid a second, lossy conversion pass.
///
/// Not for export.
void spanAddEvent(Span span, String name, Map<String, Attribute>? attributes) {
  span._innerSpan.addEvent(name, attributes: convertAttributes(attributes));
}

/// Record an exception with already-typed internal [Attribute] values.
///
/// Counterpart to [spanAddEvent] for the exception path; see its note about
/// avoiding a double conversion.
///
/// Not for export.
void spanRecordException(
  Span span,
  dynamic exception, {
  StackTrace stackTrace = StackTrace.empty,
  Map<String, Attribute>? attributes,
}) {
  span._innerSpan.recordException(
    exception,
    stackTrace: stackTrace,
    attributes: convertAttributes(attributes),
  );
}
