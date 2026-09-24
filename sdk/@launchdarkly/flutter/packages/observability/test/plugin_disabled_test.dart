import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/ld_observe.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/options/session_replay_options.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/ld_observe_plugin.dart';
import 'package:opentelemetry/api.dart' as otel;
import 'package:opentelemetry/sdk.dart' show TracerProviderBase;

import 'support/native_api_mock.dart';

/// `ObservabilityOptions.isEnabled: false`. Never registers a global tracer
/// provider, so both boots can share an isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = NativeApiMock();

  setUp(() {
    native.calls.clear();
    native.install();
  });

  tearDown(native.uninstall);

  test('without session replay, nothing is recorded or forwarded', () async {
    LDObserve.trackScreenView('/opened-before-boot');

    final plugin = LDObservePlugin(
      const ObservabilityOptions(isEnabled: false),
    );
    await plugin.boot('mobile-key');

    // Native still starts: it applies `isEnabled` to its own instrumentation.
    expect(native.callsTo('start'), hasLength(1));
    expect(plugin.instrumentations, isEmpty);
    expect(otel.globalTracerProvider, isNot(isA<TracerProviderBase>()));
    expect(Otel.logRecorder, isNull);
    expect(Otel.screenViewRecorder, isNull);
    expect(Otel.clickRecorder, isNull);

    LDObserve.trackScreenView('/details');
    LDObserve.track('purchase');
    LDObserve.recordLog('hello');
    LDObserve.trackClick(id: 'buy');
    await pumpEventQueue();

    expect(native.callsTo('trackScreenView'), isEmpty);
    expect(native.callsTo('track'), isEmpty);
    expect(native.callsTo('recordLog'), isEmpty);
    expect(native.callsTo('trackClick'), isEmpty);
  });

  test('with session replay, replay signals still reach native', () async {
    final plugin = LDObservePlugin(
      const ObservabilityOptions(isEnabled: false),
      replay: const SessionReplayOptions(),
    );
    await plugin.boot('mobile-key');

    expect(plugin.instrumentations.map((i) => i.runtimeType.toString()), [
      'ClickInstrumentation',
    ]);
    expect(otel.globalTracerProvider, isNot(isA<TracerProviderBase>()));
    expect(Otel.logRecorder, isNull);

    LDObserve.trackScreenView('/details');
    LDObserve.recordLog('hello');
    await pumpEventQueue();

    expect(native.callsTo('trackScreenView').single.first, '/details');
    expect(native.callsTo('recordLog'), isEmpty);
  });
}
