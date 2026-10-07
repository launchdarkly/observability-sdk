import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/ld_observe.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/messages.g.dart'
    as wire;
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';

const _trackScreenViewChannel =
    'dev.flutter.pigeon.launchdarkly_flutter_observability.'
    'LDNativeApi.trackScreenView';

/// Screen views recorded before the pipeline is ready.
///
/// `LDObservePlugin.boot` awaits the native bridge before wiring the exporters,
/// so a navigator observer reporting the initial route on the first frame always
/// runs while `Otel.screenViewRecorder` is still null. This lives in its own
/// test file because the deferral can only be observed across a single
/// `Otel.setup`, and the global tracer provider may only be registered once per
/// isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final recordedNames = <String>[];
  final channel = BasicMessageChannel<Object?>(
    _trackScreenViewChannel,
    wire.LDNativeApi.pigeonChannelCodec,
  );

  setUpAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockDecodedMessageHandler(channel, (message) async {
          recordedNames.add((message! as List<Object?>).first! as String);
          return <Object?>[null];
        });
  });

  tearDownAll(() {
    Otel.shutdown();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockDecodedMessageHandler(channel, null);
  });

  // A plain test rather than testWidgets: Otel.setup starts the batch span
  // processor's periodic timer, which the widget binding's fake async reports as
  // a leak at the end of the test body.
  test('the screen the user ended up on is replayed once ready', () async {
    LDObserve.trackScreenView('/home');
    LDObserve.trackScreenView('/details');
    await pumpEventQueue();

    // Nothing can reach the native bridge yet, and the intermediate '/home' is
    // already stale — only the current screen is worth replaying.
    expect(recordedNames, isEmpty);

    Otel.setup('mobile-key', configWithDefaults());
    await pumpEventQueue();

    expect(recordedNames, ['/details']);
  });
}
