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
