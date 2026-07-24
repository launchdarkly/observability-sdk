import 'package:launchdarkly_flutter_observability/src/otel/conversions.dart';
import 'package:launchdarkly_flutter_observability/src/otel/exporters/exporter_factory.dart';
import 'package:launchdarkly_flutter_observability/src/otel/service_convention.dart';
import 'package:launchdarkly_flutter_observability/src/otel/symbols_id.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';
import 'package:opentelemetry/api.dart'
    show registerGlobalTracerProvider, Attribute;
import 'package:opentelemetry/sdk.dart' show TracerProviderBase, Resource;

const _highlightProjectIdAttr = 'highlight.project_id';

class Otel {
  static final List<TracerProviderBase> _tracerProviders = [];

  /// The platform-appropriate log recorder, set during [setup]. `null` before
  /// the pipeline is initialized.
  static LogRecorder? _logRecorder;
  static LogRecorder? get logRecorder => _logRecorder;

  /// The platform-appropriate track recorder, set during [setup]. `null` before
  /// the pipeline is initialized.
  static TrackRecorder? _trackRecorder;
  static TrackRecorder? get trackRecorder => _trackRecorder;

  /// The platform-appropriate identify recorder, set during [setup]. `null`
  /// before the pipeline is initialized.
  static IdentifyRecorder? _identifyRecorder;
  static IdentifyRecorder? get identifyRecorder => _identifyRecorder;

  static void setup(String sdkKey, ObservabilityConfig config) {
    // TODO: Log when otel is setup multiple times. It will work, but the
    // behavior may be confusing.

    final exporters = ObservabilityExporters.instance;

    final resourceAttributes = <Attribute>[
      Attribute.fromString(_highlightProjectIdAttr, sdkKey),
    ];
    resourceAttributes.addAll(
      convertAttributes(
        ServiceConvention.getAttributes(
          serviceName: config.applicationName,
          serviceVersion: config.applicationVersion,
        ),
      ),
    );
    // In obfuscated release builds, report the Dart snapshot build id so the
    // backend can symbolicate crashes against the uploaded .symbols map. Absent
    // in debug/profile builds and on web, where it is null (and skipped).
    // Web/Dart export path: attach the symbols id to the Dart OTel Resource,
    // which the OTLP exporter serializes. On mobile the Dart Resource is not
    // sent over the pigeon bridge, so the id is also injected into the native
    // init options (see LDObservePlugin.boot) to reach native-exported signals.
    final symbolsId = readSymbolsId();
    if (symbolsId != null) {
      resourceAttributes.add(
        Attribute.fromString(symbolsIdAttributeKey, symbolsId),
      );
    }
    final tracerProvider = TracerProviderBase(
      processors: exporters.createSpanProcessors(config),
      resource: Resource(resourceAttributes),
    );

    _tracerProviders.add(tracerProvider);

    registerGlobalTracerProvider(tracerProvider);

    _logRecorder = exporters.createLogRecorder(config);
    _trackRecorder = exporters.createTrackRecorder(config);
    _identifyRecorder = exporters.createIdentifyRecorder(config);
  }

  static void shutdown() {
    for (final tracerProvider in _tracerProviders) {
      tracerProvider.shutdown();
    }
    _tracerProviders.clear();
    _logRecorder = null;
    _trackRecorder = null;
    _identifyRecorder = null;
  }
}
