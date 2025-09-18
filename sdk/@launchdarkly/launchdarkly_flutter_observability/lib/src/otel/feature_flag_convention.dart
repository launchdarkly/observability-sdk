import 'dart:convert';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/src/otel/stringable_attribute.dart';

const _featureFlagEventName = 'feature_flag';
const _featureFlagKeyAttr = '$_featureFlagEventName.key';

// Feature flag set.
const _featureFlagSetAttr = '$_featureFlagEventName.set';
const _featureFlagSetIdAttr = '$_featureFlagSetAttr.id';

// Feature flag provider.
const _featureFlagProviderAttr = '$_featureFlagEventName.provider';
const _featureFlagProviderNameAttr = '$_featureFlagProviderAttr.name';

// Feature flag context.
const _featureFlagContextAttr = '$_featureFlagEventName.context';
const _featureFlagContextIdAttr = '$_featureFlagContextAttr.id';

// Feature flag result.
const _featureFlagResultAttr = '$_featureFlagEventName.result';
const _featureFlagResultValueAttr = '$_featureFlagResultAttr.value';
const _featureFlagResultVariationIndex =
    '$_featureFlagResultAttr.variationIndex';
const _featureFlagResultReasonAttr = '$_featureFlagResultAttr.reason';
const _featureFlagResultReasonInExperimentAttr =
    '$_featureFlagResultReasonAttr.inExperiment';

final providerNameAttribute = StringableAttribute.fromString(
  _featureFlagProviderNameAttr,
  'LaunchDarkly',
);

String? _toJsonValue(LDValue value) {
  try {
    return jsonEncode(value.toDynamic());
  } catch (_) {
    // All LDValues must be JSON presentable, but this is for safety.
    return null;
  }
}

List<StringableAttribute> _getValueAttributes(LDEvaluationDetail<LDValue> detail) {
  final attributes = <StringableAttribute>[];
  final jsonValue = _toJsonValue(detail.value);
  if (jsonValue != null) {
    attributes.add(
      StringableAttribute.fromString(_featureFlagResultValueAttr, jsonValue),
    );
  }
  if (detail.variationIndex != null) {
    attributes.add(
      StringableAttribute.fromInt(
        _featureFlagResultVariationIndex,
        detail.variationIndex!,
      ),
    );
  }
  if (detail.reason != null && detail.reason!.inExperiment) {
    attributes.add(
      StringableAttribute.fromBoolean(
        _featureFlagResultReasonInExperimentAttr,
        detail.reason!.inExperiment,
      ),
    );
  }
  return attributes;
}

/// Class representing the feature flagging semantic convention with
/// LaunchDarkly additions.
class FeatureFlagConvention {
  /// Get feature_flag event attributes.
  static List<StringableAttribute> getEventAttributes({
    required String key,
    required LDEvaluationDetail<LDValue> detail,
    LDContext? context,
    String? environmentId,
  }) {
    List<StringableAttribute> attributes = [
      providerNameAttribute,
      StringableAttribute.fromString(_featureFlagKeyAttr, key),
      ..._getValueAttributes(detail),
    ];

    if (context != null && context.valid) {
      attributes.add(
        StringableAttribute.fromString(_featureFlagContextIdAttr, context.canonicalKey),
      );
    }

    if (environmentId != null) {
      attributes.add(
        StringableAttribute.fromString(_featureFlagSetIdAttr, environmentId!),
      );
    }
    return attributes;
  }

  /// The name to use for the event.
  static const eventName = _featureFlagEventName;
}
