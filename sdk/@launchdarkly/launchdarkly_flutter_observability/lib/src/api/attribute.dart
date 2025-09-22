/// Base class representing an open telemetry attribute.
///
/// Attributes may be a boolean, integer, double, string, list of booleans,
/// list of integers, list of doubles, or list of strings.
///
/// There is a type-safe attribute constructor for each attribute type, or
/// alternatively [Attribute.fromDynamic] can be used. If the dynamic attribute
/// is not a compatible type, then the attribute will not be added.
/// ```dart
/// span.setAttribute('my-integer', IntAttribute(42));
/// span.setAttribute('my-string', StringAttribute('test'));
/// span.setAttribute('from-dynamic-value', Attribute.fromDynamic(value));
/// ```
sealed class Attribute {
  factory Attribute.fromDynamic(dynamic value) {
    if (value is bool) {
      return BooleanAttribute(value);
    }
    if (value is int) {
      return IntAttribute(value);
    }
    if (value is double) {
      return DoubleAttribute(value);
    }
    if (value is String) {
      return StringAttribute(value);
    }
    if (value is List<String>) {
      return StringListAttribute(value);
    }
    if (value is List<double>) {
      return DoubleListAttribute(value);
    }
    if (value is List<int>) {
      return IntListAttribute(value);
    }
    if (value is List<bool>) {
      return BooleanListAttribute(value);
    }
    return InvalidAttribute._internal();
  }
  Attribute._internal();
}

/// When using [fromDynamic] it is possible to get a value that cannot be
/// represented as an attribute. When this happens an [InvalidAttribute] will
/// be created. This attribute will be omitted from otel data.
final class InvalidAttribute extends Attribute {
  InvalidAttribute._internal() : super._internal();

  @override
  String toString() => 'InvalidAttribute()';
}

/// An integer attribute.
final class IntAttribute extends Attribute {
  final int value;
  IntAttribute(this.value) : super._internal();

  @override
  String toString() => 'IntAttribute{value: $value}';
}

/// A double attribute.
final class DoubleAttribute extends Attribute {
  final double value;

  DoubleAttribute(this.value) : super._internal();

  @override
  String toString() => 'DoubleAttribute{value: $value}';
}

/// A boolean attribute.
final class BooleanAttribute extends Attribute {
  final bool value;

  BooleanAttribute(this.value) : super._internal();

  @override
  String toString() => 'BooleanAttribute{value: $value}';
}

/// A string attribute.
final class StringAttribute extends Attribute {
  final String value;

  StringAttribute(this.value) : super._internal();

  @override
  String toString() => 'StringAttribute{value: $value}';
}

/// An attribute containing a list of strings.
final class StringListAttribute extends Attribute {
  late final List<String> value;

  StringListAttribute(List<String> input)
    : value = List.unmodifiable(input),
      super._internal();

  @override
  String toString() => 'StringListAttribute{value: $value}';
}

/// An attribute containing a list of doubles.
final class DoubleListAttribute extends Attribute {
  late final List<double> value;

  DoubleListAttribute(List<double> input)
    : value = List.unmodifiable(input),
      super._internal();

  @override
  String toString() => 'DoubleListAttribute{value: $value}';
}

/// An attribute containing a list of integers.
final class IntListAttribute extends Attribute {
  late final List<int> value;

  IntListAttribute(List<int> input)
    : value = List.unmodifiable(input),
      super._internal();

  @override
  String toString() => 'IntListAttribute{value: $value}';
}

/// An attribute containing a list of booleans.
final class BooleanListAttribute extends Attribute {
  late final List<bool> value;

  BooleanListAttribute(List<bool> input)
    : value = List.unmodifiable(input),
      super._internal();

  @override
  String toString() => 'BooleanListAttribute{value: $value}';
}
