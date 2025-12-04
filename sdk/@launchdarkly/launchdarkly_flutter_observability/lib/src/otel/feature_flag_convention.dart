import 'dart:convert';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';

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

final _providerNameAttributeValue = StringAttribute('LaunchDarkly');

String? _toJsonValue(LDValue value) {
  try {
    return jsonEncode(value.toDynamic());
  } catch (_) {
    // All LDValues must be JSON presentable, but this is for safety.
    return null;
  }
}

Map<String, Attribute> _getValueAttributes(LDEvaluationDetail<LDValue> detail) {
  final attributes = <String, Attribute>{};
  final jsonValue = _toJsonValue(detail.value);
  if (jsonValue != null) {
    attributes[_featureFlagResultValueAttr] = StringAttribute(jsonValue);
  }
  if (detail.variationIndex != null) {
    attributes[_featureFlagResultVariationIndex] = IntAttribute(
      detail.variationIndex!,
    );
  }
  if (detail.reason != null && detail.reason!.inExperiment) {
    attributes[_featureFlagResultReasonInExperimentAttr] = BooleanAttribute(
      detail.reason!.inExperiment,
    );
  }
  return attributes;
}

/// Class representing the feature flagging semantic convention with
/// LaunchDarkly additions.
class FeatureFlagConvention {
  /// Get feature_flag event attributes.
  static Map<String, Attribute> getEventAttributes({
    required String key,
    required LDEvaluationDetail<LDValue> detail,
    LDContext? context,
    String? environmentId,
  }) {
    Map<String, Attribute> attributes = {
      _featureFlagProviderNameAttr: _providerNameAttributeValue,
      _featureFlagKeyAttr: StringAttribute(key),
      ..._getValueAttributes(detail),
    };

    if (context != null && context.valid) {
      attributes[_featureFlagContextIdAttr] = StringAttribute(
        context.canonicalKey,
      );
    }

    if (environmentId != null) {
      attributes[_featureFlagSetIdAttr] = StringAttribute(environmentId);
    }
    return attributes;
  }

  /// The name to use for the event.
  static const eventName = _featureFlagEventName;
}
