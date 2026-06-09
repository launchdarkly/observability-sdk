import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:launchdarkly_flutter_observability/src/otel/track_convention.dart';

Matcher matchesAttribute(String key, dynamic value) {
  return predicate<MapEntry<String, Attribute>>((entry) {
    if (entry.key != key) return false;
    final attr = entry.value;
    return switch (attr) {
      StringAttribute() => attr.value == value,
      IntAttribute() => attr.value == value,
      BooleanAttribute() => attr.value == value,
      DoubleAttribute() => attr.value == value,
      _ => false,
    };
  }, 'has key "$key" and value "$value"');
}

void main() {
  test('uses the native "track" span name', () {
    expect(TrackConvention.spanName, equals('track'));
  });

  test('includes the event key with no data or metric value', () {
    final attributes = TrackConvention.getSpanAttributes(eventName: 'checkout');

    expect(attributes.length, equals(1));
    expect(attributes.entries, contains(matchesAttribute('key', 'checkout')));
  });

  test('includes the numeric metric value as a double', () {
    final attributes = TrackConvention.getSpanAttributes(
      eventName: 'purchase',
      metricValue: 42,
    );

    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('key', 'purchase'),
        matchesAttribute('value', 42.0),
      ]),
    );
  });

  test('omits the metric value when null', () {
    final attributes = TrackConvention.getSpanAttributes(eventName: 'purchase');

    expect(attributes['value'], isNull);
  });

  test('flattens scalar members of object data', () {
    final data = LDValue.buildObject()
        .addValue('plan', LDValue.ofString('pro'))
        .addValue('count', LDValue.ofNum(3))
        .addValue('trial', LDValue.ofBool(true))
        .build();

    final attributes = TrackConvention.getSpanAttributes(
      eventName: 'subscribe',
      data: data,
    );

    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('plan', 'pro'),
        matchesAttribute('count', 3.0),
        matchesAttribute('trial', true),
        matchesAttribute('key', 'subscribe'),
      ]),
    );
  });

  test('flattens homogeneous array members of object data', () {
    final data = LDValue.buildObject()
        .addValue(
          'tags',
          LDValue.buildArray()
              .addValue(LDValue.ofString('a'))
              .addValue(LDValue.ofString('b'))
              .build(),
        )
        .build();

    final attributes = TrackConvention.getSpanAttributes(
      eventName: 'tagged',
      data: data,
    );

    final tags = attributes['tags'];
    expect(tags, isA<StringListAttribute>());
    expect((tags as StringListAttribute).value, equals(['a', 'b']));
  });

  test('skips members that cannot be represented as attributes', () {
    final data = LDValue.buildObject()
        .addValue(
          'nested',
          LDValue.buildObject()
              .addValue('inner', LDValue.ofString('x'))
              .build(),
        )
        .addValue(
          'mixed',
          LDValue.buildArray()
              .addValue(LDValue.ofString('a'))
              .addValue(LDValue.ofNum(1))
              .build(),
        )
        .build();

    final attributes = TrackConvention.getSpanAttributes(
      eventName: 'edge',
      data: data,
    );

    expect(attributes['nested'], isNull);
    expect(attributes['mixed'], isNull);
    expect(attributes.entries, contains(matchesAttribute('key', 'edge')));
  });

  test('ignores non-object data payloads', () {
    final attributes = TrackConvention.getSpanAttributes(
      eventName: 'scalar',
      data: LDValue.ofString('not-an-object'),
    );

    expect(attributes.length, equals(1));
    expect(attributes.entries, contains(matchesAttribute('key', 'scalar')));
  });

  test('includes context kind/key pairs for a valid context', () {
    final context = LDContextBuilder()
        .kind('user', 'user-key')
        .kind('org', 'org-key')
        .build();

    final attributes = TrackConvention.getSpanAttributes(
      eventName: 'evt',
      context: context,
    );

    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('user', 'user-key'),
        matchesAttribute('org', 'org-key'),
      ]),
    );
  });

  test('excludes context keys when the context is invalid', () {
    final invalidContext = LDContextBuilder().kind('user', '').build();

    final attributes = TrackConvention.getSpanAttributes(
      eventName: 'evt',
      context: invalidContext,
    );

    expect(attributes['user'], isNull);
  });

  test('reserved key/value take precedence over user data', () {
    final data = LDValue.buildObject()
        .addValue('key', LDValue.ofString('should-not-win'))
        .addValue('value', LDValue.ofString('should-not-win'))
        .build();

    final attributes = TrackConvention.getSpanAttributes(
      eventName: 'evt',
      data: data,
      metricValue: 5,
    );

    expect(attributes.entries, contains(matchesAttribute('key', 'evt')));
    expect(attributes.entries, contains(matchesAttribute('value', 5.0)));
  });
}
