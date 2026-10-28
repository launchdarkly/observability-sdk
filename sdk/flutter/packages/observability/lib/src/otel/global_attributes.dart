import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:opentelemetry/api.dart' as otel;

import '../api/attribute.dart';
import 'conversions.dart';

/// The process-wide attributes set through `LDObserve.setAttributes`, applied
/// to every span, log and forwarded event recorded after the change.
///
/// Readers only ever see an immutable snapshot, rebuilt on each change, so the
/// recording hot path does no copying or conversion. The store is independent
/// of the pipeline: it can be written before init, survives session rotation
/// (which happens natively and never reaches it), and is never persisted.
///
/// Not for export.
final class GlobalAttributes {
  GlobalAttributes._();

  /// Keys the SDK manages for session correlation. Setting them globally would
  /// misattribute telemetry to another session, so they are ignored.
  static const reservedKeys = {'session.id', 'highlight.session_id'};

  static _Snapshot _snapshot = _Snapshot.empty;

  /// The current attributes, keyed by name.
  static Map<String, Attribute> get attributes => _snapshot.attributes;

  /// The current attributes as plain Dart values, for the native bridge.
  static Map<String, Object> get values => _snapshot.values;

  /// The current attributes in OpenTelemetry form, for span processors.
  static List<otel.Attribute> get otelAttributes => _snapshot.otelAttributes;

  /// Whether no global attributes are set.
  static bool get isEmpty => _snapshot.values.isEmpty;

  /// Merges [properties] into the global attributes by key. Reserved keys and
  /// values that cannot be represented as an attribute are ignored.
  static void set(Map<String, Object?> properties) {
    final next = Map<String, Object>.of(_snapshot.values);
    var changed = false;
    properties.forEach((key, value) {
      if (reservedKeys.contains(key)) {
        return;
      }
      final attribute = attributeFromNative(value);
      final otelAttribute = convertAttribute(key, attribute);
      if (otelAttribute == null) {
        return;
      }
      next[key] = otelAttribute.value;
      changed = true;
    });
    if (changed) {
      _snapshot = _Snapshot(next);
    }
  }

  /// Removes [keys] from the global attributes.
  static void remove(List<String> keys) {
    if (keys.every((key) => !_snapshot.values.containsKey(key))) {
      return;
    }
    final next = Map<String, Object>.of(_snapshot.values)
      ..removeWhere((key, _) => keys.contains(key));
    _snapshot = _Snapshot(next);
  }

  /// [attributes] with every global attribute whose key it lacks added.
  /// Returns [attributes] itself when there is nothing to add.
  static Map<String, Attribute>? fillAttributes(
    Map<String, Attribute>? attributes,
  ) {
    final globals = _snapshot.attributes;
    if (globals.isEmpty) {
      return attributes;
    }
    return {...globals, ...?attributes};
  }

  /// [properties] with every global value whose key it lacks added. Returns
  /// [properties] itself when there is nothing to add.
  static Map<String, Object?>? fillProperties(
    Map<String, Object?>? properties,
  ) {
    final globals = _snapshot.values;
    if (globals.isEmpty) {
      return properties;
    }
    return {...globals, ...?properties};
  }

  /// Clears every global attribute.
  @visibleForTesting
  static void resetForTesting() => _snapshot = _Snapshot.empty;
}

final class _Snapshot {
  factory _Snapshot(Map<String, Object> values) {
    final attributes = values.map(
      (key, value) => MapEntry(key, attributeFromNative(value)),
    );
    return _Snapshot._(
      Map.unmodifiable(values),
      Map.unmodifiable(attributes),
      List.unmodifiable(convertAttributes(attributes)),
    );
  }

  _Snapshot._(this.values, this.attributes, this.otelAttributes);

  static final empty = _Snapshot(const {});

  final Map<String, Object> values;
  final Map<String, Attribute> attributes;
  final List<otel.Attribute> otelAttributes;
}
