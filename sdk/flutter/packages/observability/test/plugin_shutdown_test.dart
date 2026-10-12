import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/click/click_instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/lifecycle/lifecycle_instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/ld_observe.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/options/session_replay_options.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/ld_observe_plugin.dart';

import 'support/native_api_mock.dart';

/// Default options, then `LDObserve.shutdown`. In its own file because shutdown
/// is terminal for the isolate and the enabled boot registers the global tracer
/// provider, which is allowed once per isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = NativeApiMock();

  setUpAll(native.install);

  tearDownAll(() {
    ClickInstrumentation.detachDetector();
    native.uninstall();
  });

  test('shutdown is terminal and stops session replay', () async {
    final plugin = LDObservePlugin(
      const ObservabilityOptions(),
      replay: const SessionReplayOptions(),
    );
    await plugin.boot('mobile-key');

    expect(
      plugin.instrumentations.whereType<LifecycleInstrumentation>(),
      hasLength(1),
      reason: 'appLifecycle defaults to true',
    );

    ClickInstrumentation.attachDetector();
    await pumpEventQueue();
    expect(native.callsTo('setEmbedderClickHandling').last, [true]);

    LDObserve.shutdown();
    LDObserve.shutdown();
    await pumpEventQueue();

    expect(native.callsTo('shutdown'), hasLength(1));
    // Native gets tap reporting back rather than losing clicks entirely.
    expect(native.callsTo('setEmbedderClickHandling').last, [false]);
    expect(plugin.instrumentations, isEmpty);
    expect(Otel.logRecorder, isNull);
    expect(Otel.screenViewRecorder, isNull);

    native.calls.clear();
    LDObserve.trackScreenView('/after-shutdown');
    LDObserve.track('purchase');
    LDObserve.recordLog('hello');
    final restarted = await LDObserve.initStandalone(
      'mobile-key',
      observability: const ObservabilityOptions(),
    );
    await pumpEventQueue();

    expect(restarted, isFalse);

    expect(native.calls, isEmpty);
    expect(Otel.screenViewRecorder, isNull);
  });
}
