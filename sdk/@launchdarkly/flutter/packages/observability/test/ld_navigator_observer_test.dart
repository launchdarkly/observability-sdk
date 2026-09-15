import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/navigation/ld_navigator_observer.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/messages.g.dart'
    as wire;
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';

const _trackScreenViewChannel =
    'dev.flutter.pigeon.launchdarkly_flutter_observability.'
    'LDNativeApi.trackScreenView';

/// A route with [name], or an anonymous one when [name] is null — standing in
/// for the dialogs and bottom sheets that `showDialog` and friends push without
/// route settings.
Route<void> _route(String? name) => PageRouteBuilder<void>(
  settings: RouteSettings(name: name),
  pageBuilder: (_, _, _) => const SizedBox.shrink(),
);

/// Drives the observer through a real [Navigator] rather than by invoking its
/// callbacks directly, so these exercise when Flutter actually reports a change
/// of topmost route instead of restating this file's assumptions about it.
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
    Otel.setup('mobile-key', configWithDefaults());
  });

  setUp(recordedNames.clear);

  tearDownAll(() {
    Otel.shutdown();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockDecodedMessageHandler(channel, null);
  });

  /// Boots an app sitting on `/home` and returns its navigator.
  Future<NavigatorState> pumpApp(WidgetTester tester) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigatorKey,
        navigatorObservers: [LDNavigatorObserver()],
        initialRoute: '/home',
        onGenerateRoute: (settings) => _route(settings.name),
      ),
    );
    await tester.pumpAndSettle();
    return navigatorKey.currentState!;
  }

  testWidgets('records the screen the app opens on', (tester) async {
    await pumpApp(tester);

    expect(recordedNames, ['/home']);
  });

  testWidgets('records a pushed screen and the one it reveals on pop', (
    tester,
  ) async {
    final navigator = await pumpApp(tester);

    unawaited(navigator.push(_route('/details')));
    await tester.pumpAndSettle();
    navigator.pop();
    await tester.pumpAndSettle();

    expect(recordedNames, ['/home', '/details', '/home']);
  });

  testWidgets('does not duplicate a screen when an unnamed overlay pops', (
    tester,
  ) async {
    final navigator = await pumpApp(tester);

    unawaited(navigator.push(_route(null)));
    await tester.pumpAndSettle();
    navigator.pop();
    await tester.pumpAndSettle();

    expect(recordedNames, ['/home']);
  });

  testWidgets('records the revealed screen when the top route is removed', (
    tester,
  ) async {
    final navigator = await pumpApp(tester);
    final details = _route('/details');

    unawaited(navigator.push(details));
    await tester.pumpAndSettle();
    navigator.removeRoute(details);
    await tester.pumpAndSettle();

    expect(recordedNames, ['/home', '/details', '/home']);
  });

  testWidgets('records nothing when a route below the top is removed', (
    tester,
  ) async {
    final navigator = await pumpApp(tester);
    final details = _route('/details');

    unawaited(navigator.push(details));
    await tester.pumpAndSettle();
    unawaited(navigator.push(_route('/settings')));
    await tester.pumpAndSettle();
    navigator.removeRoute(details);
    await tester.pumpAndSettle();

    expect(recordedNames, ['/home', '/details', '/settings']);
  });

  testWidgets('records the underlying screen after an unnamed replacement', (
    tester,
  ) async {
    final navigator = await pumpApp(tester);

    unawaited(navigator.push(_route('/details')));
    await tester.pumpAndSettle();
    unawaited(navigator.pushReplacement(_route(null)));
    await tester.pumpAndSettle();
    navigator.pop();
    await tester.pumpAndSettle();

    expect(recordedNames, ['/home', '/details', '/home']);
  });

  testWidgets('records only the destination of pushAndRemoveUntil', (
    tester,
  ) async {
    final navigator = await pumpApp(tester);

    unawaited(navigator.push(_route('/details')));
    await tester.pumpAndSettle();
    unawaited(
      navigator.pushAndRemoveUntil(
        _route('/checkout'),
        (route) => route.isFirst,
      ),
    );
    await tester.pumpAndSettle();

    expect(recordedNames, ['/home', '/details', '/checkout']);
  });
}
