import 'dart:collection';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:launchdarkly_flutter_observability/src/api/span_status_code.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/debug_print.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/lifecycle/lifecycle_instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/otel/feature_flag_convention.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';

import '../api/span.dart';
import '../observe.dart';

const _launchDarklyObservabilityName = 'launchdarkly-observability';
const _launchDarklyObservabilityPluginName =
    '$_launchDarklyObservabilityName-plugin';

// SDK-metadata attribute keys mirrored from the JS reference plugin
// (`sdk/highlight-run/src/integrations/launchdarkly/index.ts`).
const _telemetrySdkNameAttr = 'telemetry.sdk.name';
const _telemetrySdkVersionAttr = 'telemetry.sdk.version';
const _featureFlagSetIdAttr = 'feature_flag.set.id';
const _featureFlagProviderNameAttr = 'feature_flag.provider.name';
const _launchDarklyApplicationIdAttr = 'launchdarkly.application.id';
const _launchDarklyApplicationVersionAttr = 'launchdarkly.application.version';
const _launchDarklyProviderName = 'LaunchDarkly';

/// Build the SDK-metadata attribute bag for `launchdarkly.track` spans from the
/// plugin's [PluginEnvironmentMetadata]. Exported as `package-private` so the
/// observability plugin's `register` can cache it onto [Observe] and so unit
/// tests can verify the mapping directly.
Map<String, Attribute> buildSdkMetadataAttributes(
  PluginEnvironmentMetadata environmentMetadata,
) {
  final attributes = <String, Attribute>{
    _telemetrySdkNameAttr: StringAttribute(environmentMetadata.sdk.name),
    _telemetrySdkVersionAttr: StringAttribute(environmentMetadata.sdk.version),
    _featureFlagSetIdAttr: StringAttribute(
      environmentMetadata.credential.value,
    ),
    _featureFlagProviderNameAttr: StringAttribute(_launchDarklyProviderName),
  };

  final application = environmentMetadata.application;
  if (application != null) {
    // `ApplicationInfo.applicationId` is non-required at the storage layer
    // (sanitization can null it out even though the constructor takes a
    // required String). Only emit attributes when the values are present.
    final applicationId = application.applicationId;
    if (applicationId != null) {
      attributes[_launchDarklyApplicationIdAttr] = StringAttribute(
        applicationId,
      );
    }
    final applicationVersion = application.applicationVersion;
    if (applicationVersion != null) {
      attributes[_launchDarklyApplicationVersionAttr] = StringAttribute(
        applicationVersion,
      );
    }
  }

  return attributes;
}

/// Build the bare LaunchDarkly context-key attribute bag (e.g.
/// `{user: 'alice', org: 'team-a'}`) from an [LDContext]. Mirrors the JS
/// `getContextKeys` helper but emits typed [StringAttribute] values directly.
Map<String, Attribute> buildContextKeyAttributes(LDContext context) {
  final attributes = <String, Attribute>{};
  context.keys.forEach((kind, key) {
    attributes[kind] = StringAttribute(key);
  });
  return attributes;
}

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

  @override
  UnmodifiableMapView<String, dynamic> afterIdentify(
    IdentifySeriesContext hookContext,
    UnmodifiableMapView<String, dynamic> data,
    IdentifyResult result,
  ) {
    // Cache the bare LD context-key attributes so subsequent
    // `Observe.track(...)` (or `afterTrack`) calls can spread them onto the
    // `launchdarkly.track` span without re-deriving them from the context.
    try {
      setLDContextKeyAttributes(buildContextKeyAttributes(hookContext.context));
    } catch (_) {
      // Hook safety: identify must always return normally.
    }
    return data;
  }

  @override
  void afterTrack(TrackSeriesContext hookContext) {
    // Thin pass-through to the public `Observe.track`. All gating, attribute
    // assembly, and exception handling lives there. The defensive try/catch
    // here is belt-and-suspenders: `Observe.track` already swallows internal
    // failures, but wrapping the call site guarantees that the hook continues
    // to satisfy the `ldClient.track(...)` "must always return normally"
    // contract even if a future refactor moves work into `Observe.track`'s
    // prologue (before its own try/catch).
    try {
      Observe.track(
        hookContext.key,
        data: hookContext.data,
        numericValue: hookContext.numericValue,
      );
    } catch (_) {
      // Hook safety: LDClient.track() must always return normally.
    }
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
    InstrumentationConfig? instrumentation,
    ProductAnalyticsConfig? productAnalytics,
  }) : _config = configWithDefaults(
         applicationName: applicationName,
         applicationVersion: applicationVersion,
         otlpEndpoint: otlpEndpoint,
         backendUrl: backendUrl,
         contextFriendlyName: contextFriendlyName,
         instrumentationConfig: instrumentation,
         productAnalyticsConfig: productAnalytics,
       ) {
    _instrumentations.add(LifecycleInstrumentation());
    _instrumentations.add(
      DebugPrintInstrumentation(_config.instrumentationConfig),
    );
  }

  @override
  void register(
    LDClient client,
    PluginEnvironmentMetadata environmentMetadata,
  ) {
    registerPlugin(this, environmentMetadata.credential.value, _config);
    setLDSdkMetadataAttributes(buildSdkMetadataAttributes(environmentMetadata));
    super.register(client, environmentMetadata);
  }

  @override
  List<Hook> get hooks => [_ObservabilityHook()];

  @override
  PluginMetadata get metadata => _metadata;

  /// Unregister any event handlers used by the plugin and cleanup any
  /// resources requiring manual cleanup.
  void dispose() {
    for (final instrumentation in _instrumentations) {
      instrumentation.dispose();
    }
  }
}
