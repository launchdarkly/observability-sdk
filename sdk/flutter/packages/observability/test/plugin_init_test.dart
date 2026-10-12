import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/ld_observe.dart';
import 'package:launchdarkly_flutter_observability/src/observe_otel.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/otel/setup.dart';

import 'support/native_api_mock.dart';

/// Init readiness: failure, retry, and repeated init. Sequential and in its own
/// file because a successful boot registers the global tracer provider, which
/// is allowed once per isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = NativeApiMock();

  setUpAll(native.install);

  tearDownAll(() {
    ObserveOtel.shutdown();
    native.uninstall();
  });

  test('a failed start reports false, and a later init retries', () async {
    native.failStart = true;
    final failed = await LDObserve.initStandalone(
      'mobile-key',
      observability: const ObservabilityOptions(),
    );

    expect(failed, isFalse);
    expect(Otel.logRecorder, isNull);

    native.failStart = false;
    final retried = await LDObserve.initStandalone(
      'mobile-key',
      observability: const ObservabilityOptions(),
    );

    expect(retried, isTrue);
    expect(native.callsTo('start'), hasLength(2));
    expect(Otel.logRecorder, isNotNull);
  });

  test('a repeated init is ignored and reports the first outcome', () async {
    final repeated = await LDObserve.initStandalone(
      'another-key',
      observability: const ObservabilityOptions(isEnabled: false),
    );

    expect(repeated, isTrue);
    expect(native.callsTo('start'), hasLength(2));
    // The ignored options did not replace the running pipeline.
    expect(Otel.logRecorder, isNotNull);
  });
}
