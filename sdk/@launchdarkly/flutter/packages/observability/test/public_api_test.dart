import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart'
    show LDClient;
// Only the public barrel: a symbol dropped from it, or a signature changed,
// fails to compile here.
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

/// The 1.0 public contract: exported symbols, `LDObserve` signatures, and
/// documented option defaults.
void main() {
  group('LDObserve signatures', () {
    test('match the 1.0 API', () {
      final Future<bool> Function(
        LDClient, {
        required ObservabilityOptions observability,
        SessionReplayOptions? replay,
      })
      init = LDObserve.init;
      final Future<bool> Function(
        String, {
        required ObservabilityOptions observability,
        SessionReplayOptions? replay,
      })
      initStandalone = LDObserve.initStandalone;
      final Span Function(
        String, {
        SpanKind kind,
        Map<String, Object?>? properties,
      })
      startSpan = LDObserve.startSpan;
      final T Function<T>(
        String,
        T Function(Span), {
        SpanKind kind,
        Map<String, Object?>? properties,
      })
      withSpan = LDObserve.withSpan;
      final void Function(
        String, {
        Map<String, Object?>? properties,
        num? metricValue,
      })
      track = LDObserve.track;
      final void Function(
        String, {
        String? screenClass,
        String? screenId,
        String? category,
        Map<String, Object?>? properties,
      })
      trackScreenView = LDObserve.trackScreenView;
      final void Function({
        String? id,
        String? tag,
        String? text,
        double? x,
        double? y,
        Map<String, Object?>? properties,
      })
      trackClick = LDObserve.trackClick;
      final void Function(
        dynamic, {
        StackTrace? stackTrace,
        Map<String, Object?>? properties,
      })
      recordException = LDObserve.recordException;
      final void Function(
        String, {
        LogSeverity severity,
        StackTrace? stackTrace,
        Map<String, Object?>? properties,
      })
      recordLog = LDObserve.recordLog;
      final ZoneSpecification Function() zoneSpecification =
          LDObserve.zoneSpecification;
      final Future<void> Function() shutdown = LDObserve.shutdown;

      expect([
        init,
        initStandalone,
        startSpan,
        withSpan,
        track,
        trackScreenView,
        trackClick,
        recordException,
        recordLog,
        zoneSpecification,
        shutdown,
      ], everyElement(isNotNull));
    });

    test('Span methods match the 1.0 API', () {
      void checkSpan(Span span) {
        final void Function() end = span.end;
        final void Function(String, Object?) setAttribute = span.setAttribute;
        final void Function(Map<String, Object?>) setAttributes =
            span.setAttributes;
        final void Function(
          dynamic, {
          StackTrace stackTrace,
          Map<String, Object?>? attributes,
        })
        recordException = span.recordException;
        final void Function(String, {Map<String, Object?>? attributes})
        addEvent = span.addEvent;
        final void Function(SpanStatusCode) setStatus = span.setStatus;
        expect([
          end,
          setAttribute,
          setAttributes,
          recordException,
          addEvent,
        ], everyElement(isNotNull));
        expect(setStatus, isNotNull);
      }

      expect(checkSpan, isNotNull);
    });
  });

  group('enums', () {
    test('LogSeverity carries the OTel severity numbers', () {
      expect(
        {for (final s in LogSeverity.values) s.name: s.severityNumber},
        {
          'trace': 1,
          'debug': 5,
          'info': 9,
          'warn': 13,
          'error': 17,
          'fatal': 21,
        },
      );
    });

    test('ObservabilityLogLevel carries the OTel severity numbers', () {
      expect(ObservabilityLogLevel.values, hasLength(25));
      expect(ObservabilityLogLevel.trace.severity, 1);
      expect(ObservabilityLogLevel.info.severity, 9);
      expect(ObservabilityLogLevel.fatal4.severity, 24);
      expect(ObservabilityLogLevel.none.severity, 0x7fffffff);
    });

    test('SpanKind and SpanStatusCode values', () {
      expect(
        SpanKind.values.map((e) => e.name),
        unorderedEquals([
          'server',
          'client',
          'producer',
          'consumer',
          'internal',
        ]),
      );
      expect(
        SpanStatusCode.values.map((e) => e.name),
        unorderedEquals(['unset', 'error', 'ok']),
      );
    });
  });

  group('documented defaults', () {
    test('ObservabilityOptions', () {
      const options = ObservabilityOptions();
      expect(options.isEnabled, isTrue);
      expect(options.serviceName, 'observability-flutter');
      expect(options.serviceName, ObservabilityOptions.defaultServiceName);
      expect(options.serviceVersion, isNull);
      expect(
        options.otlpEndpoint,
        'https://otel.observability.app.launchdarkly.com:4318',
      );
      expect(
        options.backendUrl,
        'https://pub.observability.app.launchdarkly.com',
      );
      expect(options.contextFriendlyName, isNull);
      expect(options.attributes, isNull);
      expect(options.customHeaders, isEmpty);
      expect(options.sessionBackgroundTimeout, const Duration(minutes: 15));
      expect(options.logsApiLevel, ObservabilityLogLevel.info);
      expect(options.traces.includeErrors, isTrue);
      expect(options.traces.includeSpans, isTrue);
      expect(options.metricsEnabled, isTrue);
    });

    test('InstrumentationOptions', () {
      const options = InstrumentationOptions();
      expect(options.launchTimes, isTrue);
      expect(options.crashReporting, isTrue);
      expect(options.debugPrint, same(DebugPrintSetting.releaseOnly()));
    });

    test('AnalyticsOptions', () {
      const options = AnalyticsOptions();
      expect(options.taps, isTrue);
      expect(options.views, isTrue);
      expect(options.trackEvents, isTrue);
      expect(options.appLifecycle, isTrue);
      expect(options.appLaunch, isTrue);
      expect(options.customClickTargetResolver, isNull);

      const disabled = AnalyticsOptions.disabled;
      expect([
        disabled.taps,
        disabled.views,
        disabled.trackEvents,
        disabled.appLifecycle,
        disabled.appLaunch,
      ], everyElement(isFalse));
    });

    test('SessionReplayOptions', () {
      const options = SessionReplayOptions();
      expect(options.isEnabled, isTrue);
      expect(options.serviceName, 'sessionreplay-flutter');
      expect(options.sampleRate, 1.0);
      expect(options.frameRate, 1.0);
      expect(options.scale, 1.0);
      expect(options.imageQuality, 0.3);
    });

    test('PrivacyOptions', () {
      const options = PrivacyOptions();
      expect(options.maskTextInputs, isTrue);
      expect(options.maskWebViews, isFalse);
      expect(options.maskLabels, isFalse);
      expect(options.maskImages, isFalse);
      expect(options.minimumAlpha, 0.02);
      expect(options.maskWidgetTypes, isEmpty);
      expect(options.maskWidgetKeys, isEmpty);
      expect(options.unmaskWidgetTypes, isEmpty);
      expect(options.unmaskWidgetKeys, isEmpty);
      expect(options.ignoreWidgetTypes, isEmpty);
      expect(options.ignoreWidgetKeys, isEmpty);
      expect(options.maskClickText, isFalse);
    });
  });

  group('widgets', () {
    test('are constructible from the barrel', () {
      const child = SizedBox();
      final widgets = <Widget>[
        const SessionReplayCapture(child: child),
        const LDMask(child: child),
        const LDUnmask(child: child),
        const LDIgnore(child: child),
        const LDClick(id: 'buy', child: child),
      ];
      expect(widgets, hasLength(5));

      final NavigatorObserver observer = LDNavigatorObserver(
        screenNameExtractor: LDRoutePatterns.extractor(['/orders/:id']),
      );
      expect(observer, isA<LDNavigatorObserver>());

      LDClickTargetInfo? resolver(Widget widget) =>
          const LDClickTargetInfo(tag: 'PrimaryButton');
      final LDClickTargetResolver typed = resolver;
      expect(typed(child)?.tag, 'PrimaryButton');
    });
  });
}
