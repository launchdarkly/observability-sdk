import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import '../api/attribute.dart';

/// Semantic convention for custom `track` events.
///
/// Mirrors the native iOS/Android observability SDKs, which emit a span named
/// `track` carrying the event key under the reserved `key` attribute and the
/// optional numeric metric value under `value`. User-supplied `data` members
/// and the context kind/key pairs are attached as additional attributes.
class TrackConvention {
  /// The name used for the track span. Matches the native iOS/Android exporters
  /// (`SemanticConvention.trackSpanName = "track"`).
  static const spanName = 'track';

  /// Reserved attribute carrying the track event key.
  static const keyAttr = 'key';

  /// Reserved attribute carrying the numeric metric value, when present.
  static const valueAttr = 'value';

  /// Builds the span attributes for a track event.
  ///
  /// Applied in increasing precedence so the reserved keys can never be
  /// clobbered by user-supplied [data]: user data first, then the context
  /// kind/key pairs, then the reserved `key`/`value` last (matching the native
  /// track path).
  static Map<String, Attribute> getSpanAttributes({
    required String eventName,
    LDValue? data,
    num? metricValue,
    LDContext? context,
  }) {
    final attributes = <String, Attribute>{};

    if (data != null) {
      attributes.addAll(_attributesFromData(data));
    }

    if (context != null && context.valid) {
      context.keys.forEach((kind, key) {
        attributes[kind] = StringAttribute(key);
      });
    }

    attributes[keyAttr] = StringAttribute(eventName);
    if (metricValue != null) {
      attributes[valueAttr] = DoubleAttribute(metricValue.toDouble());
    }

    return attributes;
  }

  /// Flattens an object [data] payload into a flat attribute map.
  ///
  /// Mirrors the native `LDValue.toAttributes()`: only object payloads
  /// contribute members; scalar and array payloads contribute nothing. Members
  /// that cannot be represented as an OpenTelemetry attribute (nested objects,
  /// heterogeneous arrays) are skipped.
  static Map<String, Attribute> _attributesFromData(LDValue data) {
    if (data.type != LDValueType.object) {
      return const {};
    }
    final attributes = <String, Attribute>{};
    for (final key in data.keys) {
      final attribute = _attributeFromValue(data.getFor(key));
      if (attribute != null) {
        attributes[key] = attribute;
      }
    }
    return attributes;
  }

  static Attribute? _attributeFromValue(LDValue value) {
    switch (value.type) {
      case LDValueType.boolean:
        return BooleanAttribute(value.booleanValue());
      case LDValueType.number:
        return DoubleAttribute(value.doubleValue());
      case LDValueType.string:
        return StringAttribute(value.stringValue());
      case LDValueType.array:
        return _attributeFromArray(value);
      case LDValueType.object:
      case LDValueType.nullType:
        return null;
    }
  }

  static Attribute? _attributeFromArray(LDValue value) {
    final values = value.values.toList(growable: false);
    if (values.isEmpty) {
      return null;
    }
    final elementType = values.first.type;
    if (!values.every((v) => v.type == elementType)) {
      return null;
    }
    switch (elementType) {
      case LDValueType.boolean:
        return BooleanListAttribute(
          values.map((v) => v.booleanValue()).toList(growable: false),
        );
      case LDValueType.number:
        return DoubleListAttribute(
          values.map((v) => v.doubleValue()).toList(growable: false),
        );
      case LDValueType.string:
        return StringListAttribute(
          values.map((v) => v.stringValue()).toList(growable: false),
        );
      case LDValueType.array:
      case LDValueType.object:
      case LDValueType.nullType:
        return null;
    }
  }
}
