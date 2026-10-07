import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/click/click_instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/options/session_replay_options.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/ld_observe_plugin.dart';

import 'support/native_api_mock.dart';

/// `PrivacyOptions.maskClickText` reaching click capture. Booted with
/// `isEnabled: false` so no global tracer provider is registered and the boots
/// can share an isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = NativeApiMock();

  setUp(() {
    LDObservePlugin.resetActiveForTesting();
    native.install();
  });

  tearDown(native.uninstall);

  Future<ClickInstrumentation> bootClickCapture(PrivacyOptions privacy) async {
    final plugin = LDObservePlugin(
      const ObservabilityOptions(isEnabled: false),
      replay: SessionReplayOptions(privacy: privacy),
    );
    await plugin.boot('mobile-key');
    return plugin.instrumentations.whereType<ClickInstrumentation>().single;
  }

  test('click text is captured by default', () async {
    final click = await bootClickCapture(const PrivacyOptions());
    expect(click.captureText, isTrue);
  });

  test('maskClickText: true stops click text capture', () async {
    final click = await bootClickCapture(
      const PrivacyOptions(maskClickText: true),
    );
    expect(click.captureText, isFalse);
  });
}
