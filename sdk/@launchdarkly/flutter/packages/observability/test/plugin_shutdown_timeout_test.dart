import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/ld_observe.dart';
import 'package:launchdarkly_flutter_observability/src/observe_otel.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/options/session_replay_options.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/ld_observe_plugin.dart';

import 'support/native_api_mock.dart';

/// `LDObserve.shutdown` when native never replies. In its own file because
/// shutdown is terminal for the isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = NativeApiMock()..hangShutdown = true;

  setUpAll(native.install);
  tearDownAll(native.uninstall);

  test('shutdown completes when native never replies', () async {
    ObserveOtel.nativeShutdownTimeout = const Duration(milliseconds: 50);
    await LDObservePlugin(
      const ObservabilityOptions(isEnabled: false),
      replay: const SessionReplayOptions(),
    ).boot('mobile-key');

    await expectLater(
      LDObserve.shutdown().timeout(const Duration(seconds: 2)),
      completes,
    );
    expect(native.callsTo('shutdown'), hasLength(1));
  });
}
