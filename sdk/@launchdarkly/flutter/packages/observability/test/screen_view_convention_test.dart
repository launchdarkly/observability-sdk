import 'package:flutter_test/flutter_test.dart';

import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:launchdarkly_flutter_observability/src/otel/screen_view_convention.dart';

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
  test('uses the native "screen_view" span name', () {
    expect(ScreenViewConvention.spanName, equals('screen_view'));
  });

  test('includes only event.name with no optional classifiers', () {
    final attributes = ScreenViewConvention.getSpanAttributes(name: 'Home');

    expect(attributes.length, equals(1));
    expect(
      attributes.entries,
      contains(matchesAttribute('event.name', 'Home')),
    );
  });

  test('maps optional classifiers onto reserved event.* keys', () {
    final attributes = ScreenViewConvention.getSpanAttributes(
      name: 'Cart',
      screenClass: 'CartPage',
      screenId: 'cart-1',
      category: 'commerce',
    );

    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('event.name', 'Cart'),
        matchesAttribute('event.screen_class', 'CartPage'),
        matchesAttribute('event.screen_id', 'cart-1'),
        matchesAttribute('event.category', 'commerce'),
      ]),
    );
  });

  test('omits classifiers that are null', () {
    final attributes = ScreenViewConvention.getSpanAttributes(name: 'Home');

    expect(attributes['event.screen_class'], isNull);
    expect(attributes['event.screen_id'], isNull);
    expect(attributes['event.category'], isNull);
  });

  test('attaches user properties as attributes', () {
    final attributes = ScreenViewConvention.getSpanAttributes(
      name: 'Profile',
      properties: {'tab': 'settings', 'index': 2},
    );

    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('event.name', 'Profile'),
        matchesAttribute('tab', 'settings'),
        matchesAttribute('index', 2),
      ]),
    );
  });

  test('reserved event.name takes precedence over user properties', () {
    final attributes = ScreenViewConvention.getSpanAttributes(
      name: 'Real',
      properties: {'event.name': 'should-not-win'},
    );

    expect(
      attributes.entries,
      contains(matchesAttribute('event.name', 'Real')),
    );
  });
}
