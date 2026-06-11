import 'package:launchdarkly_flutter_observability/src/api/span_kind.dart';
import 'package:launchdarkly_flutter_observability/src/api/span_status_code.dart';
import 'package:opentelemetry/api.dart' as otel;

import '../api/attribute.dart';

/// Not for export.
otel.Attribute? convertAttribute(String name, Attribute attribute) {
  switch (attribute) {
    case IntAttribute():
      return otel.Attribute.fromInt(name, attribute.value);
    case DoubleAttribute():
      return otel.Attribute.fromDouble(name, attribute.value);
    case BooleanAttribute():
      return otel.Attribute.fromBoolean(name, attribute.value);
    case StringAttribute():
      return otel.Attribute.fromString(name, attribute.value);
    case StringListAttribute():
      return otel.Attribute.fromStringList(name, attribute.value);
    case DoubleListAttribute():
      return otel.Attribute.fromDoubleList(name, attribute.value);
    case IntListAttribute():
      return otel.Attribute.fromIntList(name, attribute.value);
    case BooleanListAttribute():
      return otel.Attribute.fromBooleanList(name, attribute.value);
    case InvalidAttribute():
      return null;
  }
}

/// Not for export.
List<otel.Attribute> convertAttributes(Map<String, Attribute>? attributes) {
  if (attributes == null) {
    return [];
  }
  final otelAttributes = <otel.Attribute>[];
  attributes.forEach((name, attribute) {
    final otelAttribute = convertAttribute(name, attribute);
    if (otelAttribute != null) {
      otelAttributes.add(otelAttribute);
    }
  });
  return otelAttributes;
}

/// Converts a plain Dart value into an [Attribute].
///
/// The public Flutter API takes native dictionaries (`Map<String, Object?>`)
/// rather than the `Attribute` types, so this is the single choke point that
/// maps native values onto the OTel attribute model. Homogeneous lists
/// (including `List<dynamic>` produced by JSON decoding) are coerced into the
/// matching typed list attribute; anything that cannot be represented (nested
/// maps, mixed lists, `null`, etc.) becomes an [InvalidAttribute] and is later
/// dropped by [convertAttribute].
///
/// Not for export.
Attribute attributeFromNative(Object? value) {
  if (value is List) {
    final listAttribute = _listAttributeFromNative(value);
    if (listAttribute != null) {
      return listAttribute;
    }
  }
  return Attribute.fromDynamic(value);
}

Attribute? _listAttributeFromNative(List<Object?> values) {
  if (values.isEmpty) {
    return null;
  }
  if (values.every((e) => e is String)) {
    return StringListAttribute(values.cast<String>());
  }
  if (values.every((e) => e is bool)) {
    return BooleanListAttribute(values.cast<bool>());
  }
  if (values.every((e) => e is int)) {
    return IntListAttribute(values.cast<int>());
  }
  if (values.every((e) => e is num)) {
    return DoubleListAttribute(
      values.map((e) => (e as num).toDouble()).toList(),
    );
  }
  return null;
}

/// Converts a native dictionary into a `Map<String, Attribute>` for the
/// internal OTel pipeline. Unrepresentable values are kept as
/// [InvalidAttribute] entries and dropped during [convertAttributes].
///
/// Not for export.
Map<String, Attribute> attributesFromProperties(
  Map<String, Object?>? properties,
) {
  if (properties == null) {
    return const {};
  }
  final attributes = <String, Attribute>{};
  properties.forEach((name, value) {
    attributes[name] = attributeFromNative(value);
  });
  return attributes;
}

/// Not for export.
otel.SpanKind convertKind(SpanKind kind) {
  switch (kind) {
    case SpanKind.server:
      return otel.SpanKind.server;
    case SpanKind.client:
      return otel.SpanKind.client;
    case SpanKind.producer:
      return otel.SpanKind.producer;
    case SpanKind.consumer:
      return otel.SpanKind.consumer;
    case SpanKind.internal:
      return otel.SpanKind.internal;
  }
}

otel.StatusCode convertSpanStatus(SpanStatusCode status) {
  switch (status) {
    case SpanStatusCode.unset:
      return otel.StatusCode.unset;
    case SpanStatusCode.error:
      return otel.StatusCode.error;
    case SpanStatusCode.ok:
      return otel.StatusCode.ok;
  }
}
