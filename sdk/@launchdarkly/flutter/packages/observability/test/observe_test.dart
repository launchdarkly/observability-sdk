import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_observability/src/observe.dart';
import 'package:opentelemetry/api.dart' as otel_api;
import 'package:opentelemetry/sdk.dart' as otel_sdk;

/// Test-only span processor that captures every finished span into an
/// in-memory list so the test can assert on the span name and attributes
/// emitted by `Observe.track`.
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

/// Tracer provider whose inner delegate is swappable. The opentelemetry-dart
/// package's `registerGlobalTracerProvider` can only be called once per
/// process; this delegating wrapper is installed once and then has its inner
/// state reset between tests. It also lets tests temporarily swap in a
/// throwing provider to exercise hook-safety code paths.
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

/// Singleton swappable provider installed once at process start. Tests obtain
/// a fresh [_RecordingSpanProcessor] via [_installRecorder] without violating
/// the opentelemetry-dart "register once" constraint.
final _SwappableTracerProvider _globalProvider = () {
  // Install the swappable wrapper exactly once. Initial inner is a no-op
  // tracer provider; [_installRecorder] swaps in a recording one per test.
  final initial = otel_sdk.TracerProviderBase();
  final wrapper = _SwappableTracerProvider(initial);
  otel_api.registerGlobalTracerProvider(wrapper);
  return wrapper;
}();

/// Reset the global tracer provider to a fresh recording one for the current
/// test, returning the new [_RecordingSpanProcessor] so the test can assert on
/// captured spans.
_RecordingSpanProcessor _installRecorder() {
  final processor = _RecordingSpanProcessor();
  final provider = otel_sdk.TracerProviderBase(processors: [processor]);
  _globalProvider.setInner(provider);
  return processor;
}

void _resetCaches() {
  // Reset the package-private caches between tests so order independence
  // holds. (`Observe.track` reads these caches as the base attribute bag.)
  setLDContextKeyAttributes(<String, Attribute>{});
  setLDSdkMetadataAttributes(<String, Attribute>{});
  setProductAnalyticsTrackEventsForTest(true);
}

/// Match a span recorded by [_RecordingSpanProcessor] against a name +
/// expected attribute subset. Only string-valued attributes are compared for
/// readability; numeric/bool attributes are inspected via direct .get(key).
Matcher _hasSpanName(String name) =>
    predicate<otel_sdk.ReadOnlySpan>((s) => s.name == name, 'name == $name');

void main() {
  group('Observe.track', () {
    setUp(_resetCaches);

    test('emits a launchdarkly.track span with the required attributes', () {
      final processor = _installRecorder();
      setLDSdkMetadataAttributes(<String, Attribute>{
        'telemetry.sdk.name': StringAttribute('launchdarkly-flutter-sdk'),
        'telemetry.sdk.version': StringAttribute('4.12.0'),
        'feature_flag.set.id': StringAttribute('mob-fake-key'),
        'feature_flag.provider.name': StringAttribute('LaunchDarkly'),
      });

      Observe.track('signup-completed');

      expect(processor.recorded, hasLength(1));
      final span = processor.recorded.single;
      expect(span, _hasSpanName('launchdarkly.track'));
      expect(span.attributes.get('key'), equals('signup-completed'));
      expect(
        span.attributes.get('telemetry.sdk.name'),
        equals('launchdarkly-flutter-sdk'),
      );
      expect(
        span.attributes.get('feature_flag.provider.name'),
        equals('LaunchDarkly'),
      );
      // No numericValue was supplied — the `value` attribute must not be set.
      expect(span.attributes.get('value'), isNull);
    });

    test('omits the value attribute when numericValue is null', () {
      final processor = _installRecorder();

      Observe.track('viewed-pricing');

      expect(processor.recorded, hasLength(1));
      expect(processor.recorded.single.attributes.get('value'), isNull);
    });

    test('sets the value attribute when numericValue is provided', () {
      final processor = _installRecorder();

      Observe.track('checkout', numericValue: 42);

      expect(processor.recorded, hasLength(1));
      // num.toDouble() — IntAttribute/DoubleAttribute is determined by our
      // helper which always converts to double for `value`.
      expect(processor.recorded.single.attributes.get('value'), equals(42.0));
    });

    test('does not spread data when data is null', () {
      final processor = _installRecorder();

      Observe.track('event', data: null);

      expect(processor.recorded, hasLength(1));
      // No data spread — only the `key` attribute is expected to be set.
      expect(processor.recorded.single.attributes.get('foo'), isNull);
    });

    test('does not spread data when data is not a Map', () {
      final processor = _installRecorder();

      Observe.track('event', data: LDValue.ofString('not-a-map'));

      expect(processor.recorded, hasLength(1));
      // No keys leaked from a non-object LDValue.
      expect(processor.recorded.single.attributes.length, lessThanOrEqualTo(1));
    });

    test('spreads data object entries as typed attributes', () {
      final processor = _installRecorder();

      final data = LDValue.buildObject()
          .addValue('foo', LDValue.ofString('bar'))
          .addValue('n', LDValue.ofNum(7))
          .addValue('enabled', LDValue.ofBool(true))
          .build();

      Observe.track('event-with-data', data: data);

      expect(processor.recorded, hasLength(1));
      final attrs = processor.recorded.single.attributes;
      expect(attrs.get('foo'), equals('bar'));
      // LDValue.ofNum(7) → toDynamic() yields int 7 (or double 7.0 depending
      // on SDK encoding); accept either via the dynamic equality check.
      final n = attrs.get('n');
      expect(n == 7 || n == 7.0, isTrue, reason: 'n was $n');
      expect(attrs.get('enabled'), equals(true));
    });

    test('skips data entries with unsupported value types', () {
      final processor = _installRecorder();

      // LDValue.objectBuilder permits nested objects; toDynamic() yields a
      // Map for the outer object whose values include both spreadable
      // primitives (string) and a nested Map (which is unsupported and must
      // be skipped without throwing).
      final data = LDValue.buildObject()
          .addValue('keep', LDValue.ofString('me'))
          .addValue(
            'nested',
            LDValue.buildObject()
                .addValue('child', LDValue.ofString('x'))
                .build(),
          )
          .build();

      Observe.track('event-with-mixed-data', data: data);

      expect(processor.recorded, hasLength(1));
      final attrs = processor.recorded.single.attributes;
      expect(attrs.get('keep'), equals('me'));
      // The nested object was skipped — it doesn't appear as a literal nor
      // does it raise.
      expect(attrs.get('nested'), isNull);
    });

    test('spreads cached context-key attributes onto the track span', () {
      final processor = _installRecorder();
      setLDContextKeyAttributes(<String, Attribute>{
        'user': StringAttribute('alice'),
        'org': StringAttribute('team-a'),
      });

      Observe.track('event');

      expect(processor.recorded, hasLength(1));
      final attrs = processor.recorded.single.attributes;
      expect(attrs.get('user'), equals('alice'));
      expect(attrs.get('org'), equals('team-a'));
    });

    test('is a no-op when ProductAnalyticsConfig.trackEvents is false', () {
      final processor = _installRecorder();
      // Simulate plugin registration with trackEvents disabled.
      registerPluginForTest(
        productAnalyticsConfig: const ProductAnalyticsConfig(
          trackEvents: false,
        ),
      );

      Observe.track('event');

      expect(processor.recorded, isEmpty);

      // Restore default for subsequent tests.
      registerPluginForTest();
    });

    test('returns normally if the underlying tracer throws', () {
      // Swap the global provider's inner to one that throws on every
      // getTracer() call so the span construction path inside `Observe.track`
      // raises. The method must catch and swallow the failure (hook-safety
      // contract).
      _globalProvider.setInner(_ThrowingTracerProvider());
      try {
        expect(() => Observe.track('event'), returnsNormally);
      } finally {
        // Restore a non-throwing inner so subsequent tests aren't poisoned.
        _globalProvider.setInner(otel_sdk.TracerProviderBase());
      }
    });

    test(
      'omits the value attribute when numericValue not specified, even with data',
      () {
        final processor = _installRecorder();
        final data = LDValue.buildObject()
            .addValue('foo', LDValue.ofString('bar'))
            .build();

        Observe.track('event', data: data);

        expect(processor.recorded, hasLength(1));
        expect(processor.recorded.single.attributes.get('value'), isNull);
      },
    );
  });

  group('attribute coercion helper', () {
    test('coerces String to StringAttribute', () {
      final attr = attributeFromDynamicForTest('hello');
      expect(attr, isA<StringAttribute>());
      expect((attr as StringAttribute).value, equals('hello'));
    });

    test('coerces int to IntAttribute', () {
      final attr = attributeFromDynamicForTest(42);
      expect(attr, isA<IntAttribute>());
      expect((attr as IntAttribute).value, equals(42));
    });

    test('coerces double to DoubleAttribute', () {
      final attr = attributeFromDynamicForTest(3.14);
      expect(attr, isA<DoubleAttribute>());
      expect((attr as DoubleAttribute).value, closeTo(3.14, 0.0001));
    });

    test('coerces bool to BooleanAttribute', () {
      final attr = attributeFromDynamicForTest(true);
      expect(attr, isA<BooleanAttribute>());
      expect((attr as BooleanAttribute).value, isTrue);
    });

    test('returns null for unsupported types', () {
      expect(attributeFromDynamicForTest(null), isNull);
      expect(attributeFromDynamicForTest(<String, dynamic>{'k': 'v'}), isNull);
      expect(attributeFromDynamicForTest(DateTime.now()), isNull);
    });

    test('coerces List<dynamic> of strings to StringListAttribute', () {
      // `LDValue.toDynamic()` always returns `List<dynamic>` for arrays — the
      // runtime type IS NOT `List<String>` even if every element is a string.
      // Verify the dynamic-list branch handles this correctly.
      final dynamic value = <dynamic>['a', 'b', 'c'];
      final attr = attributeFromDynamicForTest(value);
      expect(attr, isA<StringListAttribute>());
      expect((attr as StringListAttribute).value, equals(['a', 'b', 'c']));
    });

    test('coerces List<dynamic> of bools to BooleanListAttribute', () {
      final dynamic value = <dynamic>[true, false, true];
      final attr = attributeFromDynamicForTest(value);
      expect(attr, isA<BooleanListAttribute>());
      expect((attr as BooleanListAttribute).value, equals([true, false, true]));
    });

    test('coerces List<dynamic> of ints to IntListAttribute', () {
      final dynamic value = <dynamic>[1, 2, 3];
      final attr = attributeFromDynamicForTest(value);
      expect(attr, isA<IntListAttribute>());
      expect((attr as IntListAttribute).value, equals([1, 2, 3]));
    });

    test('coerces List<dynamic> of doubles to DoubleListAttribute', () {
      final dynamic value = <dynamic>[1.5, 2.5, 3.5];
      final attr = attributeFromDynamicForTest(value);
      expect(attr, isA<DoubleListAttribute>());
      expect((attr as DoubleListAttribute).value, equals([1.5, 2.5, 3.5]));
    });

    test('coerces mixed-numeric List<dynamic> to DoubleListAttribute', () {
      // Mixed int/double (common when `LDValue.buildArray()` mixes
      // `LDValue.ofNum(1)` and `LDValue.ofNum(2.5)`) → coerce all to double.
      final dynamic value = <dynamic>[1, 2.5, 3];
      final attr = attributeFromDynamicForTest(value);
      expect(attr, isA<DoubleListAttribute>());
      expect((attr as DoubleListAttribute).value, equals([1.0, 2.5, 3.0]));
    });

    test('returns null for empty List<dynamic>', () {
      // Empty list — element type cannot be inferred; skip rather than emit a
      // potentially-mistyped attribute.
      final dynamic value = <dynamic>[];
      expect(attributeFromDynamicForTest(value), isNull);
    });

    test('returns null for List<dynamic> with mixed unsupported types', () {
      // String + int is not coercible to any single typed-list attribute.
      final dynamic value = <dynamic>['a', 1];
      expect(attributeFromDynamicForTest(value), isNull);
    });
  });

  group('Observe.track list-attribute integration', () {
    setUp(_resetCaches);

    test(
      'spreads LDValue array data as StringListAttribute (via List<dynamic> path)',
      () {
        // This is the bug guard: `LDValue.toDynamic()` returns `List<dynamic>`
        // for arrays, NOT `List<String>`, so the typed-list-only checks used to
        // silently drop array entries from `data`. The new dynamic-list branch
        // must surface them as a typed list attribute on the span.
        final processor = _installRecorder();

        final data = LDValue.buildObject()
            .addValue(
              'tags',
              LDValue.buildArray()
                  .addValue(LDValue.ofString('a'))
                  .addValue(LDValue.ofString('b'))
                  .build(),
            )
            .build();

        Observe.track('event-with-array-data', data: data);

        expect(processor.recorded, hasLength(1));
        final attrs = processor.recorded.single.attributes;
        // The OTEL attribute store returns the underlying list value for the key
        // when present; if missing, it returns null. Assert both presence and
        // contents.
        final tags = attrs.get('tags');
        expect(tags, isNotNull, reason: 'tags attribute should be present');
        expect(tags, equals(<String>['a', 'b']));
      },
    );
  });
}

/// A tracer provider whose `getTracer` always throws. Used to exercise the
/// hook-safety try/catch inside `Observe.track`.
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

/// Test-only helper to seed the product-analytics gate without spinning up a
/// full LD client. Re-exported here (rather than from src/observe.dart) so
/// the public test surface stays minimal.
void registerPluginForTest({
  ProductAnalyticsConfig productAnalyticsConfig =
      const ProductAnalyticsConfig(),
}) {
  setProductAnalyticsTrackEventsForTest(productAnalyticsConfig.trackEvents);
}
