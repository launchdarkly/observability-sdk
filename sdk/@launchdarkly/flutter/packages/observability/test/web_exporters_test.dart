import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/otel/exporters/exporter_factory.dart';
import 'package:launchdarkly_flutter_observability/src/otel/exporters/exporter_factory_web.dart'
    as web;
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/otel/symbols_id.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';
import 'package:opentelemetry/api.dart' as api;
import 'package:opentelemetry/sdk.dart' as sdk;

/// Records ended spans in memory.
class _RecordingProcessor implements sdk.SpanProcessor {
  final List<sdk.ReadOnlySpan> ended = [];

  @override
  void onStart(sdk.ReadWriteSpan span, api.Context parentContext) {}

  @override
  void onEnd(sdk.ReadOnlySpan span) => ended.add(span);

  @override
  void shutdown() {}

  @override
  void forceFlush() {}
}

/// The web exporters are plain Dart, so their gating is tested on the VM. In
/// its own file because it registers the global tracer provider, which is
/// allowed once per isolate.
void main() {
  final processor = _RecordingProcessor();
  final ObservabilityExporters exporters = web.createObservabilityExporters();

  setUpAll(() {
    api.registerGlobalTracerProvider(
      sdk.TracerProviderBase(processors: [processor]),
    );
  });

  setUp(processor.ended.clear);

  void recordAll(ObservabilityConfig config) {
    exporters.createTrackRecorder(config).track('purchase');
    exporters.createScreenViewRecorder(config).trackScreenView('Home');
    exporters.createClickRecorder(config).trackClick(id: 'buy');
  }

  List<String> spanNames() => [for (final s in processor.ended) s.name];

  group('web recorders', () {
    test('emit spans when enabled', () {
      recordAll(configFromOptions(const ObservabilityOptions()));
      expect(spanNames(), ['track', 'screen_view', 'click']);
    });

    test('emit nothing when isEnabled is false', () {
      recordAll(
        configFromOptions(const ObservabilityOptions(isEnabled: false)),
      );
      expect(spanNames(), isEmpty);
    });

    test('follow their analytics flags', () {
      recordAll(
        configFromOptions(
          const ObservabilityOptions(
            analytics: AnalyticsOptions(
              trackEvents: false,
              views: false,
              taps: false,
            ),
          ),
        ),
      );
      expect(spanNames(), isEmpty);

      recordAll(
        configFromOptions(
          const ObservabilityOptions(analytics: AnalyticsOptions(views: false)),
        ),
      );
      expect(spanNames(), ['track', 'click']);
    });
  });

  group('resource attributes', () {
    Map<String, Object> resource(ObservabilityOptions options, {String? id}) =>
        {
          for (final a in Otel.resourceAttributes(
            'sdk-key',
            configFromOptions(options),
            symbolsId: id,
          ))
            a.key: a.value,
        };

    test('include ObservabilityOptions.attributes', () {
      final attributes = resource(
        const ObservabilityOptions(
          serviceName: 'shop',
          serviceVersion: '1.2.3',
          attributes: {
            'deployment.environment': 'staging',
            'build.number': 42,
            'tags': ['a', 'b'],
            'nested': {'not': 'supported'},
          },
        ),
      );
      expect(attributes, {
        'deployment.environment': 'staging',
        'build.number': 42,
        'tags': ['a', 'b'],
        'highlight.project_id': 'sdk-key',
        'service.name': 'shop',
        'service.version': '1.2.3',
      });
    });

    test('let the SDK attributes win on a key collision', () {
      final attributes = resource(
        const ObservabilityOptions(
          serviceName: 'shop',
          attributes: {
            'service.name': 'override',
            'highlight.project_id': 'other',
          },
        ),
      );
      expect(attributes['service.name'], 'shop');
      expect(attributes['highlight.project_id'], 'sdk-key');
    });

    test('add the symbols id unless the attributes already carry it', () {
      expect(
        resource(
          const ObservabilityOptions(),
          id: 'abc',
        )[symbolsIdAttributeKey],
        'abc',
      );
      expect(
        resource(
          const ObservabilityOptions(
            attributes: {symbolsIdAttributeKey: 'from-plugin'},
          ),
          id: 'abc',
        )[symbolsIdAttributeKey],
        'from-plugin',
      );
    });
  });
}
