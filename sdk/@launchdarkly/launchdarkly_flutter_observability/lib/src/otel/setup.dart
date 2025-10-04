import 'package:launchdarkly_flutter_observability/src/otel/conversions.dart';
import 'package:launchdarkly_flutter_observability/src/otel/service_convention.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';
import 'package:opentelemetry/api.dart'
    show registerGlobalTracerProvider, Attribute;
import 'package:opentelemetry/sdk.dart'
    show BatchSpanProcessor, CollectorExporter, TracerProviderBase, Resource;

const _highlightProjectIdAttr = 'highlight.project_id';
const _tracesSuffix = '/v1/traces';

void setup(String sdkKey, ObservabilityConfig config) {
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

  registerGlobalTracerProvider(tracerProvider);
}
