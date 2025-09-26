import 'dart:collection';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/src/api/span_status_code.dart';
import 'package:launchdarkly_flutter_observability/src/otel/feature_flag_convention.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';

import '../api/span.dart';
import '../observe.dart';

const _launchDarklyObservabilityName = 'launchdarkly-observability';
const _launchDarklyObservabilityPluginName =
    '$_launchDarklyObservabilityName-plugin';

final class _ObservabilityHook extends Hook {
  static const _evalSpanDataName = 'eval-span';
  static const _launchDarklyObservabilityHookName =
      '$_launchDarklyClientPrefix-hook';
  static const _launchDarklyClientPrefix = 'LDClient';

  final HookMetadata _metadata = const HookMetadata(
    name: _launchDarklyObservabilityHookName,
  );

  @override
  HookMetadata get metadata => _metadata;

  @override
  UnmodifiableMapView<String, dynamic> beforeEvaluation(
    EvaluationSeriesContext hookContext,
    UnmodifiableMapView<String, dynamic> data,
  ) {
    final span = Observe.startSpan(
      '$_launchDarklyClientPrefix.${hookContext.method}',
    );

    var updated = Map<String, dynamic>.from(data);
    updated[_evalSpanDataName] = span;
    return UnmodifiableMapView(updated);
  }

  @override
  UnmodifiableMapView<String, dynamic> afterEvaluation(
    EvaluationSeriesContext hookContext,
    UnmodifiableMapView<String, dynamic> data,
    LDEvaluationDetail<LDValue> detail,
  ) {
    final span = data[_evalSpanDataName] as Span?;

    if (span != null) {
      span.addEvent(
        FeatureFlagConvention.eventName,
        attributes: FeatureFlagConvention.getEventAttributes(
          key: hookContext.flagKey,
          detail: detail,
          environmentId: hookContext.environmentId,
          context: hookContext.context,
        ),
      );
      span.setStatus(SpanStatusCode.ok);
      span.end();
    }
    return data;
  }
}

/// LaunchDarkly Observability plugin.
final class ObservabilityPlugin extends Plugin {
  final PluginMetadata _metadata = const PluginMetadata(
    name: _launchDarklyObservabilityPluginName,
  );

  @override
  void register(
    LDClient client,
    PluginEnvironmentMetadata environmentMetadata,
  ) {
    setup(environmentMetadata.credential.value);
    super.register(client, environmentMetadata);
  }

  @override
  List<Hook> get hooks => [_ObservabilityHook()];

  @override
  PluginMetadata get metadata => _metadata;
}
