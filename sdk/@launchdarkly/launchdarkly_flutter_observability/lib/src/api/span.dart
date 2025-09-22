import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:opentelemetry/api.dart' as otel;

import '../otel/conversions.dart';

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
  /// The attribute may be a boolean, integer, double, string, list of booleans,
  /// list of integers, list of doubles, or list of strings.
  ///
  /// There is a type-safe attribute constructor for each attribute type, or
  /// alternatively [Attribute.fromDynamic] can be used. If the dynamic attribute
  /// is not a compatible type, then the attribute will not be added.
  ///
  /// ```dart
  /// span.setAttribute('my-integer', IntAttribute(42));
  /// span.setAttribute('my-string', StringAttribute('test'));
  /// span.setAttribute('from-dynamic-value', Attribute.fromDynamic(value));
  /// ```
  void setAttribute(String name, Attribute attribute) {
    final otelAttribute = convertAttribute(name, attribute);
    if (otelAttribute != null) {
      _innerSpan.setAttribute(otelAttribute);
    }
  }

  /// Set attributes on the span.
  /// For details about attributes refer to [setAttribute].
  void setAttributes(Map<String, Attribute> attributes) {
    _innerSpan.setAttributes(convertAttributes(attributes));
  }

  /// Record information about an exception that happened during this span.
  void recordException(
    dynamic exception, {
    StackTrace stackTrace = StackTrace.empty,
    Map<String, Attribute>? attributes,
  }) {
    // The otel library supports an "escaped" attribute, but attribute is
    // deprecated and no longer recommended, so we aren't exporting it.
    _innerSpan.recordException(
      exception,
      stackTrace: stackTrace,
      attributes: convertAttributes(attributes),
    );
  }

  /// Add an event to the span with the given attributes.
  void addEvent(String name, Map<String, Attribute>? attributes) {
    _innerSpan.addEvent(name, attributes: convertAttributes(attributes));
  }
}

/// Wrap a span with a LaunchDarkly specific API type.
///
/// Not for export.
Span wrapSpan(otel.Span span, dynamic token) {
  return Span._internal(span, token);
}
