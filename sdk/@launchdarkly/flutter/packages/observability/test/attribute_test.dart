import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';

void main() {
  group('Attribute.fromDynamic', () {
    test('creates BooleanAttribute from bool', () {
      final attribute = Attribute.fromDynamic(true);
      expect(attribute, isA<BooleanAttribute>());
      expect((attribute as BooleanAttribute).value, isTrue);
    });

    test('creates IntAttribute from int', () {
      final attribute = Attribute.fromDynamic(42);
      expect(attribute, isA<IntAttribute>());
      expect((attribute as IntAttribute).value, equals(42));
    });

    test('creates DoubleAttribute from double', () {
      final attribute = Attribute.fromDynamic(3.14);
      expect(attribute, isA<DoubleAttribute>());
      expect((attribute as DoubleAttribute).value, equals(3.14));
    });

    test('creates StringAttribute from String', () {
      final attribute = Attribute.fromDynamic('hello');
      expect(attribute, isA<StringAttribute>());
      expect((attribute as StringAttribute).value, equals('hello'));
    });

    test('creates StringListAttribute from List<String>', () {
      final input = ['a', 'b', 'c'];
      final attribute = Attribute.fromDynamic(input);
      expect(attribute, isA<StringListAttribute>());
      expect((attribute as StringListAttribute).value, equals(input));
    });

    test('creates DoubleListAttribute from List<double>', () {
      final input = [1.0, 2.5, 3.7];
      final attribute = Attribute.fromDynamic(input);
      expect(attribute, isA<DoubleListAttribute>());
      expect((attribute as DoubleListAttribute).value, equals(input));
    });

    test('creates IntListAttribute from List<int>', () {
      final input = [1, 2, 3];
      final attribute = Attribute.fromDynamic(input);
      expect(attribute, isA<IntListAttribute>());
      expect((attribute as IntListAttribute).value, equals(input));
    });

    test('creates BooleanListAttribute from List<bool>', () {
      final input = [true, false, true];
      final attribute = Attribute.fromDynamic(input);
      expect(attribute, isA<BooleanListAttribute>());
      expect((attribute as BooleanListAttribute).value, equals(input));
    });

    test('creates InvalidAttribute from unsupported type', () {
      final attribute = Attribute.fromDynamic(DateTime.now());
      expect(attribute, isA<InvalidAttribute>());
    });

    test('creates InvalidAttribute from null', () {
      final attribute = Attribute.fromDynamic(null);
      expect(attribute, isA<InvalidAttribute>());
    });

    test('creates InvalidAttribute from mixed type list', () {
      final attribute = Attribute.fromDynamic(['string', 42]);
      expect(attribute, isA<InvalidAttribute>());
    });
  });

  group('Attribute constructors', () {
    test('IntAttribute constructor works correctly', () {
      final attribute = IntAttribute(100);
      expect(attribute.value, equals(100));
    });

    test('DoubleAttribute constructor works correctly', () {
      final attribute = DoubleAttribute(2.718);
      expect(attribute.value, equals(2.718));
    });

    test('BooleanAttribute constructor works correctly', () {
      final attribute = BooleanAttribute(false);
      expect(attribute.value, isFalse);
    });

    test('StringAttribute constructor works correctly', () {
      final attribute = StringAttribute('test string');
      expect(attribute.value, equals('test string'));
    });

    test('StringListAttribute constructor works correctly', () {
      final input = ['x', 'y', 'z'];
      final attribute = StringListAttribute(input);
      expect(attribute.value, equals(input));
    });

    test('DoubleListAttribute constructor works correctly', () {
      final input = [0.1, 0.2, 0.3];
      final attribute = DoubleListAttribute(input);
      expect(attribute.value, equals(input));
    });

    test('IntListAttribute constructor works correctly', () {
      final input = [10, 20, 30];
      final attribute = IntListAttribute(input);
      expect(attribute.value, equals(input));
    });

    test('BooleanListAttribute constructor works correctly', () {
      final input = [false, true, false];
      final attribute = BooleanListAttribute(input);
      expect(attribute.value, equals(input));
    });

    test('handles extreme int values', () {
      final maxInt = IntAttribute(9223372036854775807);
      expect(maxInt.value, equals(9223372036854775807));

      final minInt = IntAttribute(-9223372036854775808);
      expect(minInt.value, equals(-9223372036854775808));
    });

    test('handles special double values', () {
      final nanAttribute = DoubleAttribute(double.nan);
      expect(nanAttribute.value.isNaN, isTrue);

      final infinityAttribute = DoubleAttribute(double.infinity);
      expect(infinityAttribute.value, equals(double.infinity));

      final negInfinityAttribute = DoubleAttribute(double.negativeInfinity);
      expect(negInfinityAttribute.value, equals(double.negativeInfinity));

      final maxDouble = DoubleAttribute(double.maxFinite);
      expect(maxDouble.value, equals(double.maxFinite));

      final minDouble = DoubleAttribute(-double.maxFinite);
      expect(minDouble.value, equals(-double.maxFinite));
    });

    test('handles empty and special strings', () {
      final emptyString = StringAttribute('');
      expect(emptyString.value, equals(''));

      final whitespaceString = StringAttribute('   ');
      expect(whitespaceString.value, equals('   '));

      final newlineString = StringAttribute('\n');
      expect(newlineString.value, equals('\n'));

      final unicodeString = StringAttribute('Hello 🌍 World! 你好');
      expect(unicodeString.value, equals('Hello 🌍 World! 你好'));
    });
  });

  group('toString methods', () {
    test('InvalidAttribute toString', () {
      final attribute =
          Attribute.fromDynamic(DateTime.now()) as InvalidAttribute;
      expect(attribute.toString(), equals('InvalidAttribute()'));
    });

    test('IntAttribute toString', () {
      final attribute = IntAttribute(42);
      expect(attribute.toString(), equals('IntAttribute{value: 42}'));
    });

    test('DoubleAttribute toString', () {
      final attribute = DoubleAttribute(3.14);
      expect(attribute.toString(), equals('DoubleAttribute{value: 3.14}'));
    });

    test('BooleanAttribute toString', () {
      final attribute = BooleanAttribute(true);
      expect(attribute.toString(), equals('BooleanAttribute{value: true}'));
    });

    test('StringAttribute toString', () {
      final attribute = StringAttribute('hello world');
      expect(
        attribute.toString(),
        equals('StringAttribute{value: hello world}'),
      );
    });

    test('StringListAttribute toString', () {
      final attribute = StringListAttribute(['a', 'b', 'c']);
      expect(
        attribute.toString(),
        equals('StringListAttribute{value: [a, b, c]}'),
      );
    });

    test('DoubleListAttribute toString', () {
      final attribute = DoubleListAttribute([1.0, 2.5]);
      expect(
        attribute.toString(),
        equals('DoubleListAttribute{value: [1.0, 2.5]}'),
      );
    });

    test('IntListAttribute toString', () {
      final attribute = IntListAttribute([1, 2, 3]);
      expect(
        attribute.toString(),
        equals('IntListAttribute{value: [1, 2, 3]}'),
      );
    });

    test('BooleanListAttribute toString', () {
      final attribute = BooleanListAttribute([true, false]);
      expect(
        attribute.toString(),
        equals('BooleanListAttribute{value: [true, false]}'),
      );
    });

    test('toString handles empty lists', () {
      final stringList = StringListAttribute([]);
      expect(stringList.toString(), equals('StringListAttribute{value: []}'));

      final intList = IntListAttribute([]);
      expect(intList.toString(), equals('IntListAttribute{value: []}'));
    });

    test('toString handles single item lists', () {
      final stringList = StringListAttribute(['single']);
      expect(
        stringList.toString(),
        equals('StringListAttribute{value: [single]}'),
      );

      final intList = IntListAttribute([42]);
      expect(intList.toString(), equals('IntListAttribute{value: [42]}'));
    });

    test('toString handles unicode strings', () {
      final unicodeString = StringAttribute('Hello 🌍 World! 你好');
      expect(
        unicodeString.toString(),
        equals('StringAttribute{value: Hello 🌍 World! 你好}'),
      );
    });
  });

  group('List attribute immutability', () {
    test('StringListAttribute value is unmodifiable', () {
      final input = ['a', 'b', 'c'];
      final attribute = StringListAttribute(input);

      // Modifying the original list shouldn't affect the attribute
      input.add('d');
      expect(attribute.value, equals(['a', 'b', 'c']));

      // The attribute's value should be unmodifiable
      expect(() => attribute.value.add('x'), throwsUnsupportedError);
    });

    test('DoubleListAttribute value is unmodifiable', () {
      final input = [1.0, 2.0, 3.0];
      final attribute = DoubleListAttribute(input);

      // Modifying the original list shouldn't affect the attribute
      input.add(4.0);
      expect(attribute.value, equals([1.0, 2.0, 3.0]));

      // The attribute's value should be unmodifiable
      expect(() => attribute.value.add(5.0), throwsUnsupportedError);
    });

    test('IntListAttribute value is unmodifiable', () {
      final input = [1, 2, 3];
      final attribute = IntListAttribute(input);

      // Modifying the original list shouldn't affect the attribute
      input.add(4);
      expect(attribute.value, equals([1, 2, 3]));

      // The attribute's value should be unmodifiable
      expect(() => attribute.value.add(5), throwsUnsupportedError);
    });

    test('BooleanListAttribute value is unmodifiable', () {
      final input = [true, false];
      final attribute = BooleanListAttribute(input);

      // Modifying the original list shouldn't affect the attribute
      input.add(true);
      expect(attribute.value, equals([true, false]));

      // The attribute's value should be unmodifiable
      expect(() => attribute.value.add(false), throwsUnsupportedError);
    });
  });
}
