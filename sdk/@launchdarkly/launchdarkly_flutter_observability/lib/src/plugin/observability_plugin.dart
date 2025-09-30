import 'dart:collection';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/src/api/span_status_code.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/lifecycle/lifecycle_instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/otel/feature_flag_convention.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';

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
  final List<Instrumentation> _instrumentations = [];
  final PluginMetadata _metadata = const PluginMetadata(
    name: _launchDarklyObservabilityPluginName,
  );

  final ObservabilityConfig _config;

  /// Construct an observability plugin with the given configuration.
  ///
  /// [applicationName] The name of the application.
  /// [applicationVersion] The version of the application. This is commonly a
  /// git SHA or semantic version.
  /// [otlpEndpoint] The OTLP endpoint for reporting OpenTelemetry data. This
  /// setting does not need to be used in most configurations.
  /// [backendUrl] The back-end URL. This setting does not need to be used in
  /// most configurations.
  /// [contextFriendlyName] A function that returns a friendly name for a given
  /// context. This name will be used to identify the session in the
  /// observability UI.
  /// ```dart
  /// ObservabilityPlugin(contextFriendlyName: (LDContext context) {
  ///   // If there is a user context with an email, then use that email.
  ///   final email = context.get('user', AttributeReference('email'));
  ///   if(email.stringValue().isNotEmpty) {
  ///     return email.stringValue();
  ///   }
  ///   // If there is no email, then use the default name.
  ///   return null;
  /// })
  /// ```
  ObservabilityPlugin({
    String? applicationName,
    String? applicationVersion,
    String? otlpEndpoint,
    String? backendUrl,
    String? Function(LDContext context)? contextFriendlyName,
  }) : _config = configWithDefaults(
         applicationName: applicationName,
         applicationVersion: applicationVersion,
         otlpEndpoint: otlpEndpoint,
         backendUrl: backendUrl,
         contextFriendlyName: contextFriendlyName,
       ) {
    _instrumentations.add(LifecycleInstrumentation());
  }

  @override
  void register(
    LDClient client,
    PluginEnvironmentMetadata environmentMetadata,
  ) {
    setup(environmentMetadata.credential.value, _config);
    super.register(client, environmentMetadata);
  }

  @override
  List<Hook> get hooks => [_ObservabilityHook()];

  @override
  PluginMetadata get metadata => _metadata;
}
