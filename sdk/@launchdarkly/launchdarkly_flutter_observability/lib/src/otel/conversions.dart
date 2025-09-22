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
