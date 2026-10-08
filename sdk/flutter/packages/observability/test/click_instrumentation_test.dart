import 'package:flutter/cupertino.dart' show CupertinoButton;
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/click/click_instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/messages.g.dart'
    as wire;
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';
import 'package:launchdarkly_flutter_observability/src/session_replay_capture.dart';

const _trackClickChannel =
    'dev.flutter.pigeon.launchdarkly_flutter_observability.'
    'LDNativeApi.trackClick';
const _handshakeChannel =
    'dev.flutter.pigeon.launchdarkly_flutter_observability.'
    'LDNativeApi.setEmbedderClickHandling';

/// The `trackClick` arguments, in the order the Pigeon schema declares them.
class RecordedClick {
  RecordedClick(List<Object?> arguments)
    : id = arguments[0] as String?,
      tag = arguments[1] as String?,
      classname = arguments[2] as String?,
      text = arguments[3] as String?,
      xpath = arguments[4] as String?,
      screenId = arguments[5] as String?,
      x = arguments[6] as int?,
      y = arguments[7] as int?,
      timestampMillis = arguments[8] as int?,
      properties = arguments[9] as Map<Object?, Object?>?;

  final String? id;
  final String? tag;
  final String? classname;
  final String? text;
  final String? xpath;
  final String? screenId;
  final int? x;
  final int? y;
  final int? timestampMillis;
  final Map<Object?, Object?>? properties;
}

/// Drives clicks through the real pointer path — `SessionReplayCapture`'s
/// detector, the widget-tree walk, and the Pigeon channel — rather than by
/// calling the instrumentation directly, so these exercise what a tap actually
/// does.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final clicks = <RecordedClick>[];
  final handshakes = <bool>[];

  final clickChannel = BasicMessageChannel<Object?>(
    _trackClickChannel,
    wire.LDNativeApi.pigeonChannelCodec,
  );
  final handshakeChannel = BasicMessageChannel<Object?>(
    _handshakeChannel,
    wire.LDNativeApi.pigeonChannelCodec,
  );

  ClickInstrumentation? instrumentation;

  setUpAll(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockDecodedMessageHandler(clickChannel, (message) async {
      clicks.add(RecordedClick(message! as List<Object?>));
      return <Object?>[null];
    });
    messenger.setMockDecodedMessageHandler(handshakeChannel, (message) async {
      handshakes.add((message! as List<Object?>).first! as bool);
      return <Object?>[null];
    });
    Otel.setup('mobile-key', configWithDefaults());
  });

  setUp(() {
    clicks.clear();
    handshakes.clear();
    instrumentation = ClickInstrumentation();
  });

  tearDown(() {
    instrumentation?.dispose();
    instrumentation = null;
  });

  tearDownAll(() {
    Otel.shutdown();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockDecodedMessageHandler(clickChannel, null);
    messenger.setMockDecodedMessageHandler(handshakeChannel, null);
  });

  Future<void> pumpApp(WidgetTester tester, {Widget? body}) async {
    await tester.pumpWidget(
      SessionReplayCapture(
        child: MaterialApp(
          home: Scaffold(
            body: Center(
              child:
                  body ??
                  ElevatedButton(
                    key: const ValueKey('pay'),
                    onPressed: () {},
                    child: const Text('Pay'),
                  ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('a tap reports the widget it landed on, once', (tester) async {
    await pumpApp(tester);

    await tester.tap(find.byType(ElevatedButton));
    await tester.pump();

    expect(clicks, hasLength(1));
    final click = clicks.single;
    expect(click.tag, equals('ElevatedButton'));
    expect(click.id, equals('pay'));
    expect(click.text, equals('Pay'));
    expect(click.xpath, endsWith('ElevatedButton#pay'));
    // The path describes the app's tree, not the wrappers this SDK adds above it.
    expect(click.xpath, isNot(contains('SessionReplayCapture')));
    expect(click.xpath, isNot(contains('LDClickDetector')));
    // Left to native, which fills it from the screen stack Dart keeps current.
    expect(click.screenId, isNull);
  });

  testWidgets('the click is stamped when the finger lifted', (tester) async {
    await pumpApp(tester);

    final before = DateTime.now().millisecondsSinceEpoch;
    await tester.tap(find.byType(ElevatedButton));
    await tester.pump();
    final after = DateTime.now().millisecondsSinceEpoch;

    // Stamped in Dart at pointer-up rather than on arrival in native, so the
    // replay marker lands with the pointer trail it belongs to.
    expect(clicks.single.timestampMillis, inInclusiveRange(before, after));
  });

  testWidgets('Android reports the physical pixels its MotionEvents use', (
    tester,
  ) async {
    await pumpApp(tester);
    final center = tester.getCenter(find.byType(ElevatedButton));
    final ratio = tester.view.devicePixelRatio;

    await tester.tap(find.byType(ElevatedButton));
    await tester.pump();

    expect(clicks.single.x, equals((center.dx * ratio).round()));
    expect(clicks.single.y, equals((center.dy * ratio).round()));
  });

  testWidgets('iOS reports the UIKit points a native tap uses', (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    try {
      await pumpApp(
        tester,
        body: CupertinoButton(onPressed: () {}, child: const Text('Pay')),
      );
      final center = tester.getCenter(find.byType(CupertinoButton));

      await tester.tap(find.byType(CupertinoButton));
      await tester.pump();

      // UIKit points are Flutter logical pixels, so they pass through unscaled.
      expect(clicks.single.x, equals(center.dx.round()));
      expect(clicks.single.y, equals(center.dy.round()));
    } finally {
      // Reset inside the body: the framework checks for leaked debug overrides
      // before `tearDown` gets a chance to run.
      debugDefaultTargetPlatformOverride = null;
    }
  });

  testWidgets('a drag is not a click', (tester) async {
    await pumpApp(tester);
    final center = tester.getCenter(find.byType(ElevatedButton));

    final gesture = await tester.startGesture(center);
    await gesture.moveBy(const Offset(kTouchSlop + 10, 0));
    await gesture.up();
    await tester.pump();

    expect(clicks, isEmpty);
  });

  testWidgets('a motionless long press is not a click', (tester) async {
    await pumpApp(tester);
    final center = tester.getCenter(find.byType(ElevatedButton));

    // Raw events, because the timestamp is the thing under test and the gesture
    // helpers stamp everything at zero.
    await tester.sendEventToBinding(
      PointerDownEvent(pointer: 7, position: center),
    );
    await tester.sendEventToBinding(
      PointerUpEvent(
        pointer: 7,
        position: center,
        timeStamp: kLongPressTimeout + const Duration(milliseconds: 100),
      ),
    );
    await tester.pump();

    expect(clicks, isEmpty);
  });

  testWidgets('a press that completes inside the timeout is a click', (
    tester,
  ) async {
    await pumpApp(tester);
    final center = tester.getCenter(find.byType(ElevatedButton));

    await tester.sendEventToBinding(
      PointerDownEvent(pointer: 8, position: center),
    );
    await tester.sendEventToBinding(
      PointerUpEvent(
        pointer: 8,
        position: center,
        timeStamp: kLongPressTimeout - const Duration(milliseconds: 100),
      ),
    );
    await tester.pump();

    expect(clicks, hasLength(1));
  });

  testWidgets('a second finger cancels the click', (tester) async {
    await pumpApp(tester);
    final center = tester.getCenter(find.byType(ElevatedButton));

    final first = await tester.startGesture(center, pointer: 1);
    final second = await tester.startGesture(center, pointer: 2);
    await first.up();
    await second.up();
    await tester.pump();

    expect(clicks, isEmpty);
  });

  testWidgets('a tap on nothing describable reports nothing', (tester) async {
    await pumpApp(tester, body: const Text('Just a label'));

    await tester.tap(find.text('Just a label'));
    await tester.pump();

    expect(clicks, isEmpty);
  });

  testWidgets('the app still receives the taps it handles', (tester) async {
    var pressed = 0;
    await pumpApp(
      tester,
      body: ElevatedButton(
        onPressed: () => pressed++,
        child: const Text('Pay'),
      ),
    );

    await tester.tap(find.byType(ElevatedButton));
    await tester.pump();

    // The detector observes pointers without entering the gesture arena.
    expect(pressed, equals(1));
    expect(clicks, hasLength(1));
  });

  group('native handshake', () {
    testWidgets('native stands down while a detector is mounted', (
      tester,
    ) async {
      await pumpApp(tester);

      expect(handshakes, equals([true]));
    });

    testWidgets('native resumes when the tree goes away', (tester) async {
      await pumpApp(tester);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();

      expect(handshakes, equals([true, false]));
    });

    testWidgets('disposing the instrumentation releases the suppression', (
      tester,
    ) async {
      await pumpApp(tester);
      instrumentation!.dispose();
      instrumentation = null;

      expect(handshakes, equals([true, false]));
    });

    testWidgets('a tree built before the plugin booted still suppresses', (
      tester,
    ) async {
      // The order every real app hits: `runApp` mounts the detector, and
      // `LDObserve.init` finishes booting a few frames later. Missing this would
      // leave native reporting the same taps Dart describes.
      instrumentation!.dispose();
      await pumpApp(tester);
      expect(handshakes, isEmpty);

      instrumentation = ClickInstrumentation();

      expect(handshakes, equals([true]));

      await tester.tap(find.byType(ElevatedButton));
      await tester.pump();
      expect(clicks, hasLength(1));
    });

    testWidgets('without instrumentation, native keeps reporting its own', (
      tester,
    ) async {
      // `analytics.taps` off: no Dart detection, so nothing may suppress the
      // coarse native clicks, which are better than none at all.
      instrumentation!.dispose();
      instrumentation = null;

      await pumpApp(tester);
      await tester.tap(find.byType(ElevatedButton));
      await tester.pump();

      expect(handshakes, isEmpty);
      expect(clicks, isEmpty);
    });
  });
}
