import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:launchdarkly_flutter_observability/src/otel/conversions.dart';

/// The Flutter public API takes native dictionaries (`Map<String, Object?>`)
/// rather than `Attribute` types. These tests cover the single conversion
/// choke point (`attributeFromNative` / `attributesFromProperties`) that maps
/// those plain values onto the OTel attribute model used internally.
void main() {
  group('attributeFromNative scalars', () {
    test('maps bool to BooleanAttribute', () {
      expect(attributeFromNative(true), isA<BooleanAttribute>());
    });

    test('maps int to IntAttribute', () {
      expect(attributeFromNative(42), isA<IntAttribute>());
    });

    test('maps double to DoubleAttribute', () {
      expect(attributeFromNative(3.14), isA<DoubleAttribute>());
    });

    test('maps String to StringAttribute', () {
      expect(attributeFromNative('flutter'), isA<StringAttribute>());
    });
  });

  group('attributeFromNative lists', () {
    test('coerces List<dynamic> of strings to StringListAttribute', () {
      final attribute = attributeFromNative(<dynamic>['a', 'b', 'c']);
      expect(attribute, isA<StringListAttribute>());
      expect((attribute as StringListAttribute).value, equals(['a', 'b', 'c']));
    });

    test('coerces List<dynamic> of ints to IntListAttribute', () {
      final attribute = attributeFromNative(<dynamic>[1, 2, 3]);
      expect(attribute, isA<IntListAttribute>());
      expect((attribute as IntListAttribute).value, equals([1, 2, 3]));
    });

    test('coerces List<dynamic> of bools to BooleanListAttribute', () {
      final attribute = attributeFromNative(<dynamic>[true, false]);
      expect(attribute, isA<BooleanListAttribute>());
      expect((attribute as BooleanListAttribute).value, equals([true, false]));
    });

    test('coerces mixed int/double list to DoubleListAttribute', () {
      final attribute = attributeFromNative(<dynamic>[1, 2.5]);
      expect(attribute, isA<DoubleListAttribute>());
      expect((attribute as DoubleListAttribute).value, equals([1.0, 2.5]));
    });
  });

  group('attributeFromNative unrepresentable values', () {
    test('drops mixed-type list as InvalidAttribute', () {
      expect(attributeFromNative(<dynamic>['a', 1]), isA<InvalidAttribute>());
    });

    test('drops nested map as InvalidAttribute', () {
      expect(
        attributeFromNative(<String, dynamic>{'k': 'v'}),
        isA<InvalidAttribute>(),
      );
    });

    test('drops null as InvalidAttribute', () {
      expect(attributeFromNative(null), isA<InvalidAttribute>());
    });

    test('drops empty list as InvalidAttribute', () {
      expect(attributeFromNative(<dynamic>[]), isA<InvalidAttribute>());
    });

    test('does not re-wrap an already-typed Attribute', () {
      // Internal convention code (feature flags, errors, etc.) produces
      // `Attribute` values directly. Those must reach the OTel pipeline through
      // the typed helpers (`spanAddEvent`/`spanRecordException`) rather than the
      // native conversion; feeding an `Attribute` back through here would drop
      // it. This guards against re-introducing that double-conversion bug.
      expect(
        attributeFromNative(StringAttribute('x')),
        isA<InvalidAttribute>(),
      );
    });
  });

  group('attributesFromProperties', () {
    test('null properties produce an empty map', () {
      expect(attributesFromProperties(null), isEmpty);
    });

    test(
      'keeps scalars and homogeneous lists, drops unrepresentable values',
      () {
        // Nested "Checkout Started" payload mirroring the Segment example from
        // analytics-taxonomy.md (§4.2): scalar fields, a flat tag list, and a
        // `products` array of line-item objects.
        final properties = <String, Object?>{
          'name': 'Checkout Started',
          'order_id': 'ord_5521',
          'value': 72.0,
          'currency': 'USD',
          'tags': <dynamic>['vip', 'first-order'],
          'products': <Map<String, dynamic>>[
            {'product_id': 'SKU-1234', 'quantity': 2, 'price': 24.0},
            {'product_id': 'SKU-9876', 'quantity': 1, 'price': 24.0},
          ],
        };

        final attributes = attributesFromProperties(properties);

        expect(attributes['name'], isA<StringAttribute>());
        expect(attributes['order_id'], isA<StringAttribute>());
        expect(attributes['value'], isA<DoubleAttribute>());
        expect(attributes['currency'], isA<StringAttribute>());
        expect(attributes['tags'], isA<StringListAttribute>());
        // Nested objects cannot be represented as a flat attribute and are
        // dropped rather than stringified.
        expect(attributes['products'], isA<InvalidAttribute>());

        // Invalid entries are filtered out when converting to OTel attributes.
        expect(convertAttributes(attributes), hasLength(5));
      },
    );
  });
}
