import 'dart:collection';

import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_observability/src/observe.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/observability_plugin.dart';
import 'package:opentelemetry/api.dart' as otel_api;
import 'package:opentelemetry/sdk.dart' as otel_sdk;

/// Test-only span processor that captures every finished span.
class _RecordingSpanProcessor implements otel_sdk.SpanProcessor {
  final List<otel_sdk.ReadOnlySpan> recorded = <otel_sdk.ReadOnlySpan>[];

  @override
  void onStart(otel_sdk.ReadWriteSpan span, otel_api.Context parentContext) {}

  @override
  void onEnd(otel_sdk.ReadOnlySpan span) {
    recorded.add(span);
  }

  @override
  void forceFlush() {}

  @override
  void shutdown() {}
}

/// Tracer provider whose inner delegate is swappable. See observe_test.dart
/// for the rationale — opentelemetry-dart's `registerGlobalTracerProvider`
/// only permits one call per isolate, so we install this wrapper once and
/// swap its inner between tests.
class _SwappableTracerProvider implements otel_api.TracerProvider {
  otel_api.TracerProvider _inner;

  _SwappableTracerProvider(this._inner);

  void setInner(otel_api.TracerProvider inner) {
    _inner = inner;
  }

  @override
  otel_api.Tracer getTracer(
    String name, {
    String? version,
    String? schemaUrl,
    List<otel_api.Attribute>? attributes,
  }) {
    return _inner.getTracer(
      name,
      version: version ?? '',
      schemaUrl: schemaUrl ?? '',
      attributes: attributes ?? const [],
    );
  }

  @override
  void forceFlush() => _inner.forceFlush();

  @override
  void shutdown() => _inner.shutdown();
}

final _SwappableTracerProvider _globalProvider = () {
  final initial = otel_sdk.TracerProviderBase();
  final wrapper = _SwappableTracerProvider(initial);
  otel_api.registerGlobalTracerProvider(wrapper);
  return wrapper;
}();

_RecordingSpanProcessor _installRecorder() {
  final processor = _RecordingSpanProcessor();
  final provider = otel_sdk.TracerProviderBase(processors: [processor]);
  _globalProvider.setInner(provider);
  return processor;
}

void _resetCaches() {
  setLDContextKeyAttributes(<String, Attribute>{});
  setLDSdkMetadataAttributes(<String, Attribute>{});
  setProductAnalyticsTrackEventsForTest(true);
}

void main() {
  // `ObservabilityPlugin()` constructs a `LifecycleInstrumentation`, which
  // touches `SchedulerBinding.instance`; the test framework requires the
  // binding to be initialized before any such access.
  TestWidgetsFlutterBinding.ensureInitialized();

  group('_ObservabilityHook.afterTrack', () {
    setUp(_resetCaches);

    test('delegates to Observe.track with key/data/numericValue', () {
      final processor = _installRecorder();
      final plugin = ObservabilityPlugin();
      final hook = plugin.hooks.single;

      final context = LDContextBuilder().kind('user', 'alice').build();
      final data = LDValue.buildObject()
          .addValue('plan', LDValue.ofString('pro'))
          .build();

      hook.afterTrack(
        TrackSeriesContext.internal(
          key: 'subscribed',
          context: context,
          data: data,
          numericValue: 9.99,
        ),
      );

      expect(processor.recorded, hasLength(1));
      final span = processor.recorded.single;
      expect(span.name, equals('launchdarkly.track'));
      expect(span.attributes.get('key'), equals('subscribed'));
      expect(span.attributes.get('value'), equals(9.99));
      expect(span.attributes.get('plan'), equals('pro'));
    });

    test('is a no-op when product analytics is disabled', () {
      final processor = _installRecorder();
      // Simulate `register()` having seeded the gate from
      // `ProductAnalyticsConfig(trackEvents: false)` — we don't spin up a
      // real LD client in this unit test, so we set the gate directly.
      setProductAnalyticsTrackEventsForTest(false);
      final plugin = ObservabilityPlugin();
      final hook = plugin.hooks.single;

      hook.afterTrack(
        TrackSeriesContext.internal(
          key: 'event',
          context: LDContextBuilder().kind('user', 'alice').build(),
          data: null,
          numericValue: null,
        ),
      );

      expect(processor.recorded, isEmpty);
    });

    test('does not throw when context-key cache is empty', () {
      final processor = _installRecorder();
      final plugin = ObservabilityPlugin();
      final hook = plugin.hooks.single;

      // No afterIdentify ran first — context-key cache is empty.
      expect(
        () => hook.afterTrack(
          TrackSeriesContext.internal(
            key: 'event',
            context: LDContextBuilder().kind('user', 'alice').build(),
            data: null,
            numericValue: null,
          ),
        ),
        returnsNormally,
      );
      expect(processor.recorded, hasLength(1));
    });

    test('afterTrack does not throw when downstream tracer throws', () {
      // Swap the global provider's inner to one that throws on every
      // getTracer() call so the span construction path inside Observe.track
      // (and therefore the hook's pass-through) is forced to raise. Hook
      // contract: `LDClient.track(...)` must always return normally, so the
      // hook MUST catch and swallow.
      _globalProvider.setInner(_ThrowingTracerProvider());
      try {
        final plugin = ObservabilityPlugin();
        final hook = plugin.hooks.single;

        expect(
          () => hook.afterTrack(
            TrackSeriesContext.internal(
              key: 'event',
              context: LDContextBuilder().kind('user', 'alice').build(),
              data: null,
              numericValue: null,
            ),
          ),
          returnsNormally,
        );
      } finally {
        // Restore a non-throwing inner so subsequent tests aren't poisoned.
        _globalProvider.setInner(otel_sdk.TracerProviderBase());
      }
    });
  });

  group('_ObservabilityHook.afterIdentify', () {
    setUp(_resetCaches);

    test(
      'populates the context-key cache so subsequent track spans include them',
      () {
        final processor = _installRecorder();
        final plugin = ObservabilityPlugin();
        final hook = plugin.hooks.single;

        final context = LDContextBuilder()
            .kind('user', 'alice')
            .kind('org', 'team-a')
            .build();

        hook.afterIdentify(
          IdentifySeriesContext.internal(context: context),
          UnmodifiableMapView(<String, dynamic>{}),
          IdentifyComplete(),
        );

        // The cache is populated; emit a track span and check the bare-key
        // spread shows up in attributes.
        Observe.track('post-identify-event');

        expect(processor.recorded, hasLength(1));
        final attrs = processor.recorded.single.attributes;
        expect(attrs.get('user'), equals('alice'));
        expect(attrs.get('org'), equals('team-a'));
      },
    );

    test('returns the data argument unchanged', () {
      final plugin = ObservabilityPlugin();
      final hook = plugin.hooks.single;
      final input = UnmodifiableMapView(<String, dynamic>{'k': 'v'});

      final result = hook.afterIdentify(
        IdentifySeriesContext.internal(
          context: LDContextBuilder().kind('user', 'alice').build(),
        ),
        input,
        IdentifyComplete(),
      );

      expect(result, same(input));
    });
  });

  group('buildSdkMetadataAttributes', () {
    test('emits required attributes when application metadata is absent', () {
      final metadata = PluginEnvironmentMetadata(
        sdk: PluginSdkMetadata(
          name: 'launchdarkly-flutter-sdk',
          version: '4.12.0',
        ),
        credential: PluginCredentialInfo(
          type: CredentialType.mobileKey,
          value: 'mob-fake-key',
        ),
      );

      final attrs = buildSdkMetadataAttributes(metadata);

      expect(
        (attrs['telemetry.sdk.name'] as StringAttribute).value,
        equals('launchdarkly-flutter-sdk'),
      );
      expect(
        (attrs['telemetry.sdk.version'] as StringAttribute).value,
        equals('4.12.0'),
      );
      expect(
        (attrs['feature_flag.set.id'] as StringAttribute).value,
        equals('mob-fake-key'),
      );
      expect(
        (attrs['feature_flag.provider.name'] as StringAttribute).value,
        equals('LaunchDarkly'),
      );
      expect(attrs.containsKey('launchdarkly.application.id'), isFalse);
      expect(attrs.containsKey('launchdarkly.application.version'), isFalse);
    });

    test(
      'includes application id/version when application metadata is provided',
      () {
        final metadata = PluginEnvironmentMetadata(
          sdk: PluginSdkMetadata(
            name: 'launchdarkly-flutter-sdk',
            version: '4.12.0',
          ),
          credential: PluginCredentialInfo(
            type: CredentialType.mobileKey,
            value: 'mob-fake-key',
          ),
          application: ApplicationInfo(
            applicationId: 'my-app',
            applicationVersion: '1.2.3',
          ),
        );

        final attrs = buildSdkMetadataAttributes(metadata);

        expect(
          (attrs['launchdarkly.application.id'] as StringAttribute).value,
          equals('my-app'),
        );
        expect(
          (attrs['launchdarkly.application.version'] as StringAttribute).value,
          equals('1.2.3'),
        );
      },
    );
  });

  group('buildContextKeyAttributes', () {
    test('emits bare top-level keys for each context kind', () {
      final context = LDContextBuilder()
          .kind('user', 'alice')
          .kind('org', 'team-a')
          .build();

      final attrs = buildContextKeyAttributes(context);

      expect((attrs['user'] as StringAttribute).value, equals('alice'));
      expect((attrs['org'] as StringAttribute).value, equals('team-a'));
    });

    test('returns an empty map for an invalid/empty context', () {
      final context = LDContextBuilder().build(); // no kinds

      final attrs = buildContextKeyAttributes(context);

      expect(attrs, isEmpty);
    });
  });
}

/// A tracer provider whose `getTracer` always throws. Used to exercise the
/// hook-safety try/catch inside `_ObservabilityHook.afterTrack` (and
/// `Observe.track`).
class _ThrowingTracerProvider implements otel_api.TracerProvider {
  @override
  otel_api.Tracer getTracer(
    String name, {
    String? version,
    String? schemaUrl,
    List<otel_api.Attribute>? attributes,
  }) {
    throw StateError('intentional test failure');
  }

  @override
  void forceFlush() {}

  @override
  void shutdown() {}
}
