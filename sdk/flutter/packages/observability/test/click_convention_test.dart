import 'package:flutter_test/flutter_test.dart';

import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:launchdarkly_flutter_observability/src/otel/click_convention.dart';

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
  test('uses the native "click" span name', () {
    expect(ClickConvention.spanName, equals('click'));
  });

  test('always identifies the event type', () {
    final attributes = ClickConvention.getSpanAttributes();

    expect(
      attributes.entries,
      contains(matchesAttribute('event.type', 'click')),
    );
  });

  test('maps a resolved widget onto the reserved event.* keys', () {
    final attributes = ClickConvention.getSpanAttributes(
      id: 'checkout.pay',
      tag: 'ElevatedButton',
      classname: 'PrimaryButton',
      text: 'Pay',
      xpath: 'Scaffold/Column/ElevatedButton#checkout.pay',
      x: 120,
      y: 480,
    );

    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('event.type', 'click'),
        matchesAttribute('event.id', 'checkout.pay'),
        matchesAttribute('event.tag', 'ElevatedButton'),
        matchesAttribute('event.classname', 'PrimaryButton'),
        matchesAttribute('event.text', 'Pay'),
        matchesAttribute(
          'event.xpath',
          'Scaffold/Column/ElevatedButton#checkout.pay',
        ),
        matchesAttribute('event.x', 120),
        matchesAttribute('event.y', 480),
      ]),
    );
  });

  test('omits fields that could not be resolved', () {
    final attributes = ClickConvention.getSpanAttributes(tag: 'InkWell');

    expect(attributes['event.id'], isNull);
    expect(attributes['event.classname'], isNull);
    expect(attributes['event.text'], isNull);
    expect(attributes['event.xpath'], isNull);
    expect(attributes['event.x'], isNull);
    expect(attributes['event.y'], isNull);
  });

  test('attaches user properties as attributes', () {
    final attributes = ClickConvention.getSpanAttributes(
      tag: 'ElevatedButton',
      properties: {'cart_size': 3, 'experiment': 'b'},
    );

    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('event.tag', 'ElevatedButton'),
        matchesAttribute('cart_size', 3),
        matchesAttribute('experiment', 'b'),
      ]),
    );
  });

  test('reserved event.* keys take precedence over user properties', () {
    final attributes = ClickConvention.getSpanAttributes(
      tag: 'ElevatedButton',
      text: 'Pay',
      properties: {'event.tag': 'spoofed', 'event.text': 'spoofed'},
    );

    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('event.tag', 'ElevatedButton'),
        matchesAttribute('event.text', 'Pay'),
      ]),
    );
  });
}
