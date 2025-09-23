import 'package:opentelemetry/api.dart';

/// Extended attribute class to add toString methods to attributes for
/// error handling, testing, and debugging purposes.
///
/// This is an internal type not to be exposed to end users.
/// I an attribute type were to be exposed, then it shouldn't indirectly expose
/// the implementation Attribute type.
final class StringableAttribute extends Attribute {
  /// Create an Attribute from a String value.
  StringableAttribute.fromString(super.key, super.value) : super.fromString();

  /// Create an Attribute from a boolean value.
  // ignore: avoid_positional_boolean_parameters
  StringableAttribute.fromBoolean(super.key, super.value) : super.fromBoolean();

  /// Create an Attribute from a double-precision floating-point value.
  StringableAttribute.fromDouble(super.key, super.value) : super.fromDouble();

  /// Create an Attribute from an integer value.
  StringableAttribute.fromInt(super.key, super.value) : super.fromInt();

  /// Create an Attribute from a list of String values.
  StringableAttribute.fromStringList(super.key, super.value) : super.fromStringList();

  /// Create an Attribute from a list of boolean values.
  StringableAttribute.fromBooleanList(super.key, super.value) : super.fromBooleanList();

  /// Create an Attribute from a list of double-precision floating-point values.
  StringableAttribute.fromDoubleList(super.key, super.value) : super.fromDoubleList();

  /// Create an Attribute from a list of integer values.
  StringableAttribute.fromIntList(super.key, super.value) : super.fromIntList();

  @override
  String toString() {
    return 'Attribute{key: $key, value: $value}';
  }
}
