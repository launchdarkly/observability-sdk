import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/options/session_replay_options.dart';
import 'package:launchdarkly_flutter_observability/src/platform/io/native_options_codec.dart';

void main() {
  group('ObservabilityOptions.toWire', () {
    test('maps native-parity defaults', () {
      final wire = const ObservabilityOptions().toWire();

      expect(wire.customHeaders, isEmpty);
      expect(wire.sessionBackgroundTimeoutMillis, 15 * 60 * 1000);
      expect(wire.logsApiLevel, ObservabilityLogLevel.info.severity);
      expect(wire.traces?.includeErrors, isTrue);
      expect(wire.traces?.includeSpans, isTrue);
      expect(wire.metricsEnabled, isTrue);
      expect(wire.analytics?.taps, isFalse);
      expect(wire.analytics?.pageViews, isTrue);
      expect(wire.analytics?.trackEvents, isTrue);
      expect(wire.instrumentation?.crashReporting, isTrue);
    });

    test('propagates custom values', () {
      final wire = const ObservabilityOptions(
        customHeaders: {'x-proxy': 'value'},
        sessionBackgroundTimeout: Duration(minutes: 5),
        logsApiLevel: ObservabilityLogLevel.none,
        traces: TracesOptions(includeErrors: false, includeSpans: false),
        metricsEnabled: false,
        analytics: AnalyticsOptions(
          taps: true,
          pageViews: false,
          trackEvents: false,
        ),
        instrumentation: InstrumentationOptions(crashReporting: false),
      ).toWire();

      expect(wire.customHeaders, {'x-proxy': 'value'});
      expect(wire.sessionBackgroundTimeoutMillis, 5 * 60 * 1000);
      expect(wire.logsApiLevel, 0x7fffffff);
      expect(wire.traces?.includeErrors, isFalse);
      expect(wire.traces?.includeSpans, isFalse);
      expect(wire.metricsEnabled, isFalse);
      expect(wire.analytics?.taps, isTrue);
      expect(wire.analytics?.pageViews, isFalse);
      expect(wire.analytics?.trackEvents, isFalse);
      expect(wire.instrumentation?.crashReporting, isFalse);
    });

    test('maps a non-default log level to its OTel severity', () {
      final wire = const ObservabilityOptions(
        logsApiLevel: ObservabilityLogLevel.warn,
      ).toWire();

      expect(wire.logsApiLevel, 13);
    });
  });

  group('SessionReplayOptions.toWire', () {
    test('uses default frame rate', () {
      final wire = const SessionReplayOptions().toWire();

      expect(wire.frameRate, 1.0);
    });

    test('propagates a custom frame rate', () {
      final wire = const SessionReplayOptions(frameRate: 4.0).toWire();

      expect(wire.frameRate, 4.0);
    });
  });
}
