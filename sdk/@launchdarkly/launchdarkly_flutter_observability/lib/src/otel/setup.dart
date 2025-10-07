import 'package:launchdarkly_flutter_observability/src/otel/conversions.dart';
import 'package:launchdarkly_flutter_observability/src/otel/service_convention.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';
import 'package:opentelemetry/api.dart'
    show registerGlobalTracerProvider, Attribute;
import 'package:opentelemetry/sdk.dart'
    show BatchSpanProcessor, CollectorExporter, TracerProviderBase, Resource;

const _highlightProjectIdAttr = 'highlight.project_id';
const _tracesSuffix = '/v1/traces';

class Otel {
  static final List<TracerProviderBase> _tracerProviders = [];

  static void setup(String sdkKey, ObservabilityConfig config) {
    // TODO: Log when otel is setup multiple times. It will work, but the
    // behavior may be confusing.

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
    final tracerProvider = TracerProviderBase(
      processors: [
        BatchSpanProcessor(
          CollectorExporter(Uri.parse('${config.otlpEndpoint}$_tracesSuffix')),
        ),
      ],
      resource: Resource(resourceAttributes),
    );

    _tracerProviders.add(tracerProvider);

    registerGlobalTracerProvider(tracerProvider);
  }

  static void shutdown() {
    for (final tracerProvider in _tracerProviders) {
      tracerProvider.shutdown();
    }
    _tracerProviders.clear();
  }
}
