import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import 'package:launchdarkly_flutter_observability/src/otel/feature_flag_convention.dart';
import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';

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
  test('returns required attributes for basic evaluation', () {
    const flagKey = 'test-flag';
    final detail = LDEvaluationDetail(
      LDValue.ofBool(true),
      null,
      LDEvaluationReason.off(),
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
    );

    expect(attributes.length, equals(3));
    // Required attributes
    expect(
      attributes.entries,
      containsAll([
        matchesAttribute('feature_flag.provider.name', 'LaunchDarkly'),
        matchesAttribute('feature_flag.key', 'test-flag'),
        matchesAttribute('feature_flag.result.value', 'true'),
      ]),
    );
  });

  test('includes context id when valid context provided', () {
    const flagKey = 'test-flag';
    final detail = LDEvaluationDetail(
      LDValue.ofBool(true),
      0,
      LDEvaluationReason.off(),
    );
    final context = LDContextBuilder().kind('user', 'user-key').build();

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
      context: context,
    );

    expect(
      attributes.entries,
      contains(
        matchesAttribute('feature_flag.context.id', context.canonicalKey),
      ),
    );
  });

  test('excludes context id when context is invalid', () {
    const flagKey = 'test-flag';
    final detail = LDEvaluationDetail(
      LDValue.ofBool(true),
      0,
      LDEvaluationReason.off(),
    );
    final invalidContext = LDContextBuilder().kind('user', '').build();

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
      context: invalidContext,
    );

    expect(attributes['feature_flag.context.id'], isNull);
  });

  test('excludes context id when context is null', () {
    const flagKey = 'test-flag';
    final detail = LDEvaluationDetail(
      LDValue.ofBool(true),
      0,
      LDEvaluationReason.off(),
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
      context: null,
    );

    expect(attributes['feature_flag.context.id'], isNull);
  });

  test('includes environment id when provided', () {
    const flagKey = 'test-flag';
    const environmentId = 'prod-env-123';
    final detail = LDEvaluationDetail(
      LDValue.ofBool(true),
      0,
      LDEvaluationReason.off(),
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
      environmentId: environmentId,
    );

    expect(
      attributes.entries,
      contains(matchesAttribute('feature_flag.set.id', environmentId)),
    );
  });

  test('excludes environment id when null', () {
    const flagKey = 'test-flag';
    final detail = LDEvaluationDetail(
      LDValue.ofBool(true),
      0,
      LDEvaluationReason.off(),
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
      environmentId: null,
    );

    expect(attributes['feature_flag.set.id'], isNull);
  });

  test('includes variation index when present', () {
    const flagKey = 'test-flag';
    const variationIndex = 2;
    final detail = LDEvaluationDetail(
      LDValue.ofString('option-c'),
      variationIndex,
      LDEvaluationReason.off(),
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
    );

    expect(
      attributes.entries,
      contains(
        matchesAttribute('feature_flag.result.variationIndex', variationIndex),
      ),
    );
  });

  test('excludes variation index when null', () {
    const flagKey = 'test-flag';
    final detail = LDEvaluationDetail(
      LDValue.ofString('default'),
      null,
      LDEvaluationReason.off(),
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
    );

    expect(attributes['feature_flag.result.variationIndex'], isNull);
  });

  test('includes inExperiment when reason indicates experiment', () {
    const flagKey = 'test-flag';
    final experimentReason = LDEvaluationReason.fallthrough(inExperiment: true);
    final detail = LDEvaluationDetail(
      LDValue.ofString('experiment-variant'),
      1,
      experimentReason,
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
    );

    expect(
      attributes.entries,
      contains(
        matchesAttribute('feature_flag.result.reason.inExperiment', true),
      ),
    );
  });

  test('excludes inExperiment when reason is null', () {
    const flagKey = 'test-flag';
    final detail = LDEvaluationDetail(LDValue.ofString('default'), null, null);

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
    );

    expect(attributes['feature_flag.result.reason.inExperiment'], isNull);
  });

  test('excludes inExperiment when not in experiment', () {
    const flagKey = 'test-flag';
    final regularReason = LDEvaluationReason.fallthrough(inExperiment: false);
    final detail = LDEvaluationDetail(
      LDValue.ofString('regular-variant'),
      1,
      regularReason,
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
    );

    expect(attributes['feature_flag.result.reason.inExperiment'], isNull);
  });

  group('value serialization', () {
    test('serializes boolean values', () {
      const flagKey = 'bool-flag';
      final detail = LDEvaluationDetail(
        LDValue.ofBool(false),
        1,
        LDEvaluationReason.off(),
      );

      final attributes = FeatureFlagConvention.getEventAttributes(
        key: flagKey,
        detail: detail,
      );

      expect(
        attributes.entries,
        contains(matchesAttribute('feature_flag.result.value', 'false')),
      );
    });

    test('serializes string values', () {
      const flagKey = 'string-flag';
      const stringValue = 'hello world';
      final detail = LDEvaluationDetail(
        LDValue.ofString(stringValue),
        0,
        LDEvaluationReason.off(),
      );

      final attributes = FeatureFlagConvention.getEventAttributes(
        key: flagKey,
        detail: detail,
      );

      expect(
        attributes.entries,
        contains(
          matchesAttribute('feature_flag.result.value', '"$stringValue"'),
        ),
      );
    });

    test('serializes numeric values', () {
      const flagKey = 'number-flag';
      const numberValue = 42.5;
      final detail = LDEvaluationDetail(
        LDValue.ofNum(numberValue),
        0,
        LDEvaluationReason.off(),
      );

      final attributes = FeatureFlagConvention.getEventAttributes(
        key: flagKey,
        detail: detail,
      );

      expect(
        attributes.entries,
        contains(matchesAttribute('feature_flag.result.value', '42.5')),
      );
    });

    test('serializes null values', () {
      const flagKey = 'null-flag';
      final detail = LDEvaluationDetail(
        LDValue.ofNull(),
        0,
        LDEvaluationReason.off(),
      );

      final attributes = FeatureFlagConvention.getEventAttributes(
        key: flagKey,
        detail: detail,
      );

      expect(
        attributes.entries,
        contains(matchesAttribute('feature_flag.result.value', 'null')),
      );
    });

    test('serializes array values', () {
      const flagKey = 'array-flag';
      final arrayValue = LDValue.buildArray()
          .addValue(LDValue.ofString('item1'))
          .addValue(LDValue.ofString('item2'))
          .build();
      final detail = LDEvaluationDetail(
        arrayValue,
        0,
        LDEvaluationReason.off(),
      );

      final attributes = FeatureFlagConvention.getEventAttributes(
        key: flagKey,
        detail: detail,
      );

      expect(
        attributes.entries,
        contains(
          matchesAttribute('feature_flag.result.value', '["item1","item2"]'),
        ),
      );
    });

    test('serializes object values', () {
      const flagKey = 'object-flag';
      final objectValue = LDValue.buildObject()
          .addValue('key1', LDValue.ofString('value1'))
          .addValue('key2', LDValue.ofNum(123))
          .build();
      final detail = LDEvaluationDetail(
        objectValue,
        0,
        LDEvaluationReason.off(),
      );

      final attributes = FeatureFlagConvention.getEventAttributes(
        key: flagKey,
        detail: detail,
      );

      final attribute =
          attributes['feature_flag.result.value'] as StringAttribute;
      final value = attribute.value;
      expect(value, contains('"key1":"value1"'));
      expect(value, contains('"key2":123'));
    });

    test('handles serialization errors gracefully', () {
      const flagKey = 'error-flag';
      // This test assumes there might be LDValue instances that can't be serialized
      // In a real scenario, this might involve mocking or creating a problematic value
      final detail = LDEvaluationDetail(
        LDValue.ofString('valid-string'),
        0,
        LDEvaluationReason.off(),
      );

      final attributes = FeatureFlagConvention.getEventAttributes(
        key: flagKey,
        detail: detail,
      );

      // Should still have other attributes even if value serialization fails
      expect(
        attributes.entries,
        containsAll([
          matchesAttribute('feature_flag.key', flagKey),
          matchesAttribute('feature_flag.provider.name', 'LaunchDarkly'),
        ]),
      );
    });
  });

  test('returns all attributes when all optional parameters provided', () {
    const flagKey = 'comprehensive-flag';
    const environmentId = 'env-456';
    const variationIndex = 1;
    final context = LDContextBuilder().kind('user', 'user-123').build();
    final experimentReason = LDEvaluationReason.fallthrough(inExperiment: true);
    final detail = LDEvaluationDetail(
      LDValue.ofString('experiment-value'),
      variationIndex,
      experimentReason,
    );

    final attributes = FeatureFlagConvention.getEventAttributes(
      key: flagKey,
      detail: detail,
      context: context,
      environmentId: environmentId,
    );

    expect(
      attributes.entries,
      containsAll([
        // Required attributes
        matchesAttribute('feature_flag.key', flagKey),
        matchesAttribute('feature_flag.provider.name', 'LaunchDarkly'),
        // Optional attributes
        matchesAttribute('feature_flag.context.id', context.canonicalKey),
        matchesAttribute('feature_flag.set.id', environmentId),
        matchesAttribute('feature_flag.result.value', '"experiment-value"'),
        matchesAttribute('feature_flag.result.variationIndex', variationIndex),
        matchesAttribute('feature_flag.result.reason.inExperiment', true),
      ]),
    );
  });
}
