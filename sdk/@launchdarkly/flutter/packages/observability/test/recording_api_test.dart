import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/api/log_severity.dart';
import 'package:launchdarkly_flutter_observability/src/ld_observe.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/messages.g.dart'
    as wire;
import 'package:launchdarkly_flutter_observability/src/plugin/observability_config.dart';

import 'support/native_api_mock.dart';

/// Typed log severity and manual click units, as they reach the native bridge.
/// In its own file because `Otel.setup` registers the global tracer provider,
/// which is allowed once per isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = NativeApiMock();

  setUpAll(() {
    native.install();
    Otel.setup('mobile-key', configWithDefaults());
  });

  setUp(native.calls.clear);

  tearDownAll(() {
    Otel.shutdown();
    native.uninstall();
  });

  test('recordLog sends the OpenTelemetry number of the severity', () async {
    LDObserve.recordLog('careful', severity: LogSeverity.warn);
    LDObserve.recordLog('default');
    await pumpEventQueue();

    final records = native
        .callsTo('recordLog')
        .map((args) => args.single! as wire.LDLogRecord)
        .toList();
    expect(records.map((r) => r.severityNumber), [13, 9]);
  });

  test(
    'trackClick converts logical pixels to Android physical pixels',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final ratio = PlatformDispatcher.instance.implicitView!.devicePixelRatio;

      LDObserve.trackClick(id: 'buy', x: 10.4, y: 20.6);
      await pumpEventQueue();

      final args = native.callsTo('trackClick').single;
      expect(args[6], (10.4 * ratio).round());
      expect(args[7], (20.6 * ratio).round());
    },
  );

  test('trackClick keeps logical pixels on iOS', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);

    LDObserve.trackClick(id: 'buy', x: 10.4, y: 20.6);
    await pumpEventQueue();

    final args = native.callsTo('trackClick').single;
    expect(args.sublist(6, 8), [10, 21]);
  });
}
