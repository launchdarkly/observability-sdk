import 'dart:async';
import 'dart:collection';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import '../api/span.dart';
import '../api/span_status_code.dart';
import '../instrumentation/debug_print.dart';
import '../instrumentation/instrumentation.dart';
import '../instrumentation/lifecycle/lifecycle_instrumentation.dart';
import '../observe_otel.dart';
import '../options/observability_options.dart';
import '../options/session_replay_options.dart';
import '../otel/feature_flag_convention.dart';
import '../platform/ld_observe_platform.dart';
import 'observability_config.dart';

const _launchDarklyObservabilityName = 'launchdarkly-observability';
const _launchDarklyObservabilityPluginName =
    '$_launchDarklyObservabilityName-plugin';

/// Hook that opens a span around each flag evaluation and records the
/// evaluation as an event. Cross-platform (Dart OpenTelemetry).
final class _ObservabilityHook extends Hook {
  static const _evalSpanDataName = 'eval-span';
  static const _launchDarklyObservabilityHookName = 'LDClient-hook';

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
    // Match the native iOS/Android exporters: a span named "evaluation" with
    // the feature flag key/provider/context set up front so the backend
    // recognizes it as a flag evaluation.
    final span = ObserveOtel.startSpan(
      FeatureFlagConvention.spanName,
      attributes: FeatureFlagConvention.getSpanAttributes(
        key: hookContext.flagKey,
        context: hookContext.context,
      ),
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
      spanAddEvent(
        span,
        FeatureFlagConvention.eventName,
        FeatureFlagConvention.getEventAttributes(
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
  void afterTrack(TrackSeriesContext hookContext) {
    // Funnel through the single track emitter so the LaunchDarkly client's
    // track path and the manual LDObserve.track API stay consistent.
    ObserveOtel.track(
      hookContext.key,
      data: hookContext.data,
      metricValue: hookContext.numericValue,
      context: hookContext.context,
    );
  }
}

/// Internal LaunchDarkly plugin that wires up the cross-platform Dart
/// OpenTelemetry pipeline together with the platform-specific session replay
/// (and, on native, the native observability bridge).
///
/// Not exported: customer code reaches it through `LDObserve.init`.
final class LDObservePlugin extends Plugin {
  final ObservabilityOptions observability;
  final SessionReplayOptions? replay;

  final ObservabilityConfig _config;
  final List<Instrumentation> _instrumentations = [];
  bool _booted = false;

  final PluginMetadata _metadata = const PluginMetadata(
    name: _launchDarklyObservabilityPluginName,
  );

  LDObservePlugin(this.observability, {this.replay})
    : _config = configFromOptions(observability);

  @override
  PluginMetadata get metadata => _metadata;

  /// Boots the Dart OpenTelemetry pipeline and the platform session replay /
  /// native stack with the given [credential]. Safe to call once; subsequent
  /// calls are ignored.
  Future<void> boot(String credential) async {
    if (_booted) {
      return;
    }
    _booted = true;

    // Start the native stack (and platform session replay) before wiring up the
    // Dart OpenTelemetry exporters. On mobile the exporters forward spans/logs
    // over the pigeon bridge to the native tracer/logger; if they are wired
    // first, early lifecycle and flag-evaluation telemetry crosses the bridge
    // while the native tracer/logger are still null and is silently dropped
    // (never gets `session.id` or reaches the backend). Awaiting start here
    // ensures the native pipeline is ready before any export can occur.
    await LDObservePlatform.instance.start(
      mobileKey: credential,
      observability: observability,
      replay: replay ?? const SessionReplayOptions(isEnabled: false),
    );

    registerPlugin(this, credential, _config);
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
    // boot() is asynchronous (the native bridge crosses the pigeon channel),
    // whereas register is synchronous; fire-and-forget so registration stays
    // non-blocking.
    unawaited(boot(environmentMetadata.credential.value));
    super.register(client, environmentMetadata);
  }

  @override
  List<Hook> get hooks => [_ObservabilityHook()];

  /// Unregister any event handlers used by the plugin and cleanup any
  /// resources requiring manual cleanup.
  void dispose() {
    for (final instrumentation in _instrumentations) {
      instrumentation.dispose();
    }
  }
}
