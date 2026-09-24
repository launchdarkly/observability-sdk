import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/debug_print.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/lifecycle/lifecycle_instrumentation.dart';
import 'package:launchdarkly_flutter_observability/src/observe_otel.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/ld_observe_plugin.dart';

import 'support/native_api_mock.dart';

/// `AnalyticsOptions.appLifecycle: false`. In its own file because an enabled
/// boot registers the global tracer provider, which is allowed once per isolate.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final native = NativeApiMock();

  setUpAll(native.install);

  tearDownAll(() {
    ObserveOtel.shutdown();
    native.uninstall();
  });

  test('appLifecycle: false skips the Dart lifecycle span', () async {
    final plugin = LDObservePlugin(
      const ObservabilityOptions(
        analytics: AnalyticsOptions(appLifecycle: false),
      ),
    );
    await plugin.boot('mobile-key');

    expect(
      plugin.instrumentations.whereType<LifecycleInstrumentation>(),
      isEmpty,
    );
    expect(
      plugin.instrumentations.whereType<DebugPrintInstrumentation>(),
      hasLength(1),
    );
  });
}
