import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:launchdarkly_flutter_observability/src/api/attribute.dart'
    show StringAttribute;
import 'package:launchdarkly_flutter_observability/src/otel/conversions.dart';
import 'package:launchdarkly_flutter_observability/src/otel/exporters/exporter_factory.dart';
import 'package:launchdarkly_flutter_observability/src/otel/service_convention.dart';
import 'package:launchdarkly_flutter_observability/src/otel/symbols_id.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';
import 'package:opentelemetry/api.dart'
    show registerGlobalTracerProvider, Attribute;
import 'package:opentelemetry/sdk.dart' show TracerProviderBase, Resource;

const _highlightProjectIdAttr = 'highlight.project_id';

/// Owns the Dart OpenTelemetry pipeline: the global tracer provider and the
/// platform-appropriate recorders every public API writes through.
class Otel {
  static final List<TracerProviderBase> _tracerProviders = [];

  static LogRecorder? _logRecorder;

  /// The platform-appropriate log recorder, set during [setup]. `null` before
  /// the pipeline is initialized.
  static LogRecorder? get logRecorder => _logRecorder;

  static TrackRecorder? _trackRecorder;

  /// The platform-appropriate track recorder, set during [setup]. `null` before
  /// the pipeline is initialized.
  static TrackRecorder? get trackRecorder => _trackRecorder;

  static IdentifyRecorder? _identifyRecorder;

  /// The platform-appropriate identify recorder, set during [setup]. `null`
  /// before the pipeline is initialized.
  static IdentifyRecorder? get identifyRecorder => _identifyRecorder;

  static ScreenViewRecorder? _screenViewRecorder;

  /// The platform-appropriate screen-view recorder, set during [setup]. `null`
  /// before the pipeline is initialized.
  static ScreenViewRecorder? get screenViewRecorder => _screenViewRecorder;

  static ClickRecorder? _clickRecorder;

  /// The platform-appropriate click recorder, set during [setup]. `null` before
  /// the pipeline is initialized.
  static ClickRecorder? get clickRecorder => _clickRecorder;

  /// The screen view recorded before the pipeline was ready, replayed at the end
  /// of [setup]. See [recordScreenView].
  static void Function(ScreenViewRecorder)? _pendingScreenView;

  /// Whether [setup] has not run yet, so a screen view is still worth holding.
  /// Cleared by both [setup] and [shutdown]: afterwards a missing recorder means
  /// screen views are not recorded at all, not that they are early.
  static bool _awaitingSetup = true;

  /// Applies [record] to the screen-view recorder, deferring it to the end of
  /// [setup] when the pipeline is not ready yet.
  ///
  /// Unlike the other signals, the opening screen view is not something the app
  /// can retry: a navigator observer reports the initial route on the first
  /// frame, which always races the asynchronous native boot. Without deferral
  /// the app would never record the screen the session starts on.
  ///
  /// Only the most recent pending screen view is kept. An earlier one is already
  /// stale by the time the pipeline is ready, and replaying it would emit a
  /// `Navigate` for a screen the user has left.
  static void recordScreenView(void Function(ScreenViewRecorder) record) {
    final recorder = _screenViewRecorder;
    if (recorder == null) {
      if (_awaitingSetup) {
        _pendingScreenView = record;
      }
      return;
    }
    record(recorder);
  }

  /// Wires the recorders and, when observability is enabled, registers the
  /// global tracer provider.
  ///
  /// With `config.enabled` false no tracer provider is registered, so spans
  /// started anywhere (flag evaluations, `LDObserve.startSpan`, exceptions) are
  /// no-ops, and logs are dropped. Screen views, clicks, track events and
  /// identifies are still forwarded when [replayEnabled], because native
  /// Session Replay builds its `Navigate`/`Click`/`Track` timeline and context
  /// from them.
  static void setup(
    String sdkKey,
    ObservabilityConfig config, {
    bool replayEnabled = false,
  }) {
    // TODO: Log when otel is setup multiple times. It will work, but the
    // behavior may be confusing.

    _awaitingSetup = false;
    final exporters = ObservabilityExporters.instance;

    if (config.enabled) {
      _setupTracing(sdkKey, config, exporters);
      _logRecorder = exporters.createLogRecorder(config);
    }

    final pendingScreenView = _pendingScreenView;
    _pendingScreenView = null;
    if (!config.enabled && !replayEnabled) {
      return;
    }

    _trackRecorder = exporters.createTrackRecorder(config);
    _identifyRecorder = exporters.createIdentifyRecorder(config);
    _clickRecorder = exporters.createClickRecorder(config);
    final screenViewRecorder = exporters.createScreenViewRecorder(config);
    _screenViewRecorder = screenViewRecorder;
    pendingScreenView?.call(screenViewRecorder);
  }

  static void _setupTracing(
    String sdkKey,
    ObservabilityConfig config,
    ObservabilityExporters exporters,
  ) {
    final tracerProvider = TracerProviderBase(
      processors: exporters.createSpanProcessors(config),
      resource: Resource(
        resourceAttributes(sdkKey, config, symbolsId: readSymbolsId()),
      ),
    );

    _tracerProviders.add(tracerProvider);

    registerGlobalTracerProvider(tracerProvider);
  }

  /// The OTel Resource attributes attached to every signal the Dart pipeline
  /// exports: the user's `ObservabilityOptions.attributes`, then the SDK's own
  /// project and service attributes, which win on a key collision.
  ///
  /// [symbolsId] is the Dart snapshot build id of an obfuscated release build
  /// (`null` in debug/profile builds and on web), reported so the backend can
  /// symbolicate crashes against the uploaded `.symbols` map. The plugin has
  /// usually already merged it into the user attributes for native; it is only
  /// added here when absent.
  @visibleForTesting
  static List<Attribute> resourceAttributes(
    String sdkKey,
    ObservabilityConfig config, {
    String? symbolsId,
  }) {
    final attributes = attributesFromProperties(config.resourceAttributes);
    if (symbolsId != null) {
      attributes.putIfAbsent(
        symbolsIdAttributeKey,
        () => StringAttribute(symbolsId),
      );
    }
    attributes[_highlightProjectIdAttr] = StringAttribute(sdkKey);
    attributes.addAll(
      ServiceConvention.getAttributes(
        serviceName: config.applicationName,
        serviceVersion: config.applicationVersion,
      ),
    );
    return convertAttributes(attributes);
  }

  /// Shuts down the tracer providers, flushing their span processors, and
  /// clears every recorder so later signals are dropped.
  static void shutdown() {
    _awaitingSetup = false;
    for (final tracerProvider in _tracerProviders) {
      tracerProvider.shutdown();
    }
    _tracerProviders.clear();
    _logRecorder = null;
    _trackRecorder = null;
    _identifyRecorder = null;
    _clickRecorder = null;
    _screenViewRecorder = null;
    _pendingScreenView = null;
  }
}
