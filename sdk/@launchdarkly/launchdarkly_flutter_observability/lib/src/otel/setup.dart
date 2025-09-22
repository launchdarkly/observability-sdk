import 'package:opentelemetry/api.dart'
    show registerGlobalTracerProvider, Attribute;
import 'package:opentelemetry/sdk.dart'
    show BatchSpanProcessor, CollectorExporter, TracerProviderBase, Resource;

const _highlightProjectIdAttr = 'highlight.project_id';
const _defaultOtlpEndpoint =
    'https://otel.observability.app.launchdarkly.com:4318';
const _defaultOtlpTracesEndpoint = '$_defaultOtlpEndpoint/v1/traces';

void setup(String sdkKey) {
  final resourceAttributes = <Attribute>[
    Attribute.fromString(_highlightProjectIdAttr, sdkKey),
  ];
  final tracerProvider = TracerProviderBase(
    processors: [
      BatchSpanProcessor(
        CollectorExporter(Uri.parse(_defaultOtlpTracesEndpoint)),
      ),
    ],
    resource: Resource(resourceAttributes),
  );

  registerGlobalTracerProvider(tracerProvider);
}
