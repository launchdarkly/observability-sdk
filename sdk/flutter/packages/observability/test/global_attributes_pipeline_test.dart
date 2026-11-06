import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_observability/src/otel/global_attributes.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/messages.g.dart'
    as wire;
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';
import 'package:opentelemetry/api.dart' as otel;
import 'package:opentelemetry/sdk.dart' as sdk;

import 'support/native_api_mock.dart';

/// Global attributes through the real pipeline, down to
/// the native bridge. In its own file because `Otel.setup` registers the global
/// tracer provider, which is allowed once per isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = NativeApiMock();

  setUpAll(() {
    native.install();
    GlobalAttributes.resetForTesting();
    // Before setup: globals set ahead of init must still apply.
    LDObserve.setAttributes({'tenant': 'acme'});
    Otel.setup('mobile-key', configWithDefaults());
    // Buffered by the batch processor until a test flushes it.
    LDObserve.startSpan('early').end();
  });

  setUp(() {
    native.calls.clear();
    GlobalAttributes.resetForTesting();
    LDObserve.setAttributes({'tenant': 'acme'});
  });

  tearDownAll(() {
    Otel.shutdown();
    native.uninstall();
    GlobalAttributes.resetForTesting();
  });

  Future<List<wire.LDSpanData>> exportedSpans() async {
    (otel.globalTracerProvider as sdk.TracerProviderBase).forceFlush();
    await pumpEventQueue();
    return [
      for (final call in native.callsTo('exportSpans'))
        ...(call.single! as List<Object?>).cast<wire.LDSpanData>(),
    ];
  }

  Future<wire.LDSpanData> exportedSpan(String name) async =>
      (await exportedSpans()).singleWhere((s) => s.name == name);

  group('spans', () {
    test('carry globals set before init', () async {
      expect((await exportedSpan('early')).attributes?['tenant'], 'acme');
    });

    test('keep attributes passed to the call over globals', () async {
      LDObserve.setAttributes({'tier': 'global'});
      LDObserve.startSpan('per-call', properties: {'tier': 'call'}).end();

      final attributes = (await exportedSpan('per-call')).attributes!;
      expect(attributes['tier'], 'call');
      expect(attributes['tenant'], 'acme');
    });

    test('keep attributes set on the span over globals', () async {
      LDObserve.startSpan('set-later')
        ..setAttribute('tenant', 'span')
        ..end();

      expect((await exportedSpan('set-later')).attributes?['tenant'], 'span');
    });

    test('take the globals current when they start', () async {
      final span = LDObserve.startSpan('long');
      LDObserve.setAttributes({'tenant': 'changed', 'late': true});
      span.end();

      final attributes = (await exportedSpan('long')).attributes!;
      expect(attributes['tenant'], 'acme');
      expect(attributes.containsKey('late'), isFalse);
    });

    test('stop carrying removed globals', () async {
      LDObserve.removeAttributes(['tenant']);
      LDObserve.startSpan('removed').end();

      expect(
        (await exportedSpan('removed')).attributes?.containsKey('tenant'),
        isFalse,
      );
    });

    test('include automatic error spans', () async {
      LDObserve.recordException(StateError('x'));

      final span = (await exportedSpans()).singleWhere(
        (s) => s.name == 'launchdarkly.error',
      );
      expect(span.attributes?['tenant'], 'acme');
    });
  });

  group('logs', () {
    Future<wire.LDLogRecord> recordedLog() async {
      await pumpEventQueue();
      return native.callsTo('recordLog').single.single! as wire.LDLogRecord;
    }

    test('carry globals, beneath the call properties', () async {
      LDObserve.setAttributes({'tier': 'global'});
      LDObserve.recordLog('hi', properties: {'tier': 'call'});

      final attributes = (await recordedLog()).attributes!;
      expect(attributes['tenant'], 'acme');
      expect(attributes['tier'], 'call');
    });
  });

  group('events forwarded to native', () {
    test('screen views carry globals beneath their properties', () async {
      LDObserve.setAttributes({'tier': 'global'});
      LDObserve.trackScreenView('Home', properties: {'tier': 'call'});
      await pumpEventQueue();

      final properties =
          native.callsTo('trackScreenView').single[4]! as Map<Object?, Object?>;
      expect(properties, {'tenant': 'acme', 'tier': 'call'});
    });

    test('clicks carry globals', () async {
      LDObserve.trackClick(id: 'buy');
      await pumpEventQueue();

      final properties =
          native.callsTo('trackClick').single[9]! as Map<Object?, Object?>;
      expect(properties, {'tenant': 'acme'});
    });

    test('track events carry globals beneath their data', () async {
      LDObserve.setAttributes({'plan': 'global'});
      LDObserve.track('purchase', properties: {'plan': 'call'});
      await pumpEventQueue();

      final data = native.callsTo('track').single[1]! as Map<Object?, Object?>;
      expect(data, {'tenant': 'acme', 'plan': 'call'});
    });
  });
}
