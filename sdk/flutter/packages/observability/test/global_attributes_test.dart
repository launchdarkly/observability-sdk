import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:launchdarkly_flutter_observability/src/otel/global_attributes.dart';
import 'package:launchdarkly_flutter_observability/src/otel/processors.dart';

/// The global attribute store and the log pipeline, without a tracer provider.
void main() {
  setUp(GlobalAttributes.resetForTesting);
  tearDown(GlobalAttributes.resetForTesting);

  group('setAttributes / removeAttributes', () {
    test('merge by key, later values replacing earlier ones', () {
      LDObserve.setAttributes({'tenant': 'acme', 'tier': 'free'});
      LDObserve.setAttributes({'tier': 'pro', 'build': 42});

      expect(GlobalAttributes.values, {
        'tenant': 'acme',
        'tier': 'pro',
        'build': 42,
      });
    });

    test('remove the given keys and ignore unknown ones', () {
      LDObserve.setAttributes({'a': 1, 'b': 2, 'c': 3});
      LDObserve.removeAttributes(['a', 'c', 'missing']);

      expect(GlobalAttributes.values, {'b': 2});
    });

    test('ignore the SDK-managed session keys', () {
      LDObserve.setAttributes({
        'session.id': 'spoofed',
        'highlight.session_id': 'spoofed',
        'kept': true,
      });

      expect(GlobalAttributes.values, {'kept': true});
    });

    test('ignore values that cannot be attributes', () {
      LDObserve.setAttributes({
        'null': null,
        'nested': {'not': 'supported'},
        'mixed': [1, 'a'],
        'list': ['a', 'b'],
        'ratio': 0.5,
      });

      expect(GlobalAttributes.values, {
        'list': ['a', 'b'],
        'ratio': 0.5,
      });
    });

    test('publish an immutable snapshot', () {
      LDObserve.setAttributes({'a': 1});
      final snapshot = GlobalAttributes.values;
      LDObserve.setAttributes({'b': 2});

      expect(snapshot, {'a': 1});
      expect(() => (snapshot as Map)['c'] = 3, throwsUnsupportedError);
    });
  });

  group('fill', () {
    test('adds only the keys a call does not set', () {
      LDObserve.setAttributes({'k': 'global', 'g': 'global'});

      expect(GlobalAttributes.fillProperties({'k': 'call'}), {
        'k': 'call',
        'g': 'global',
      });
      final attributes = GlobalAttributes.fillAttributes({
        'k': StringAttribute('call'),
      })!;
      expect((attributes['k']! as StringAttribute).value, 'call');
      expect((attributes['g']! as StringAttribute).value, 'global');
    });

    test('returns the input untouched when nothing is set', () {
      final properties = <String, Object?>{'k': 'v'};
      expect(GlobalAttributes.fillProperties(properties), same(properties));
      expect(GlobalAttributes.fillProperties(null), isNull);
    });
  });

  group('log pipeline', () {
    test('applies globals beneath the call attributes', () {
      LDObserve.setAttributes({'k': 'global', 'g': 'global'});

      final log = LogPipeline(const []).process(
        'hi',
        severity: LogSeverity.info,
        attributes: {'k': StringAttribute('call')},
      );

      expect(_values(log.attributes), {'k': 'call', 'g': 'global'});
    });

    test('runs processors in order and exports their changes', () {
      LDObserve.setAttributes({'g': 'global'});
      final seen = <Map<String, Object?>>[];

      final log =
          LogPipeline([
            _CallbackLogProcessor((record) {
              seen.add(Map.of(record.attributes));
              record.attributes['first'] = true;
              record.message = 'redacted';
            }),
            _CallbackLogProcessor((record) {
              seen.add(Map.of(record.attributes));
              record.severity = LogSeverity.warn;
              record.attributes.remove('call');
            }),
          ]).process(
            'secret',
            severity: LogSeverity.info,
            attributes: {'call': IntAttribute(1)},
          );

      expect(seen, [
        {'g': 'global', 'call': 1},
        {'g': 'global', 'call': 1, 'first': true},
      ]);
      expect(log.message, 'redacted');
      expect(log.severity, LogSeverity.warn);
      expect(_values(log.attributes), {'g': 'global', 'first': true});
    });

    test('a throwing processor is skipped and the log still exported', () {
      final log = LogPipeline([
        _CallbackLogProcessor((record) {
          record.attributes['before'] = 1;
          throw StateError('boom');
        }),
        _CallbackLogProcessor((record) => record.attributes['after'] = 2),
      ]).process('hi', severity: LogSeverity.error);

      expect(log.message, 'hi');
      expect(_values(log.attributes), {'before': 1, 'after': 2});
    });
  });
}

Map<String, Object?> _values(Map<String, Attribute>? attributes) => {
  for (final entry in {...?attributes}.entries)
    entry.key: switch (entry.value) {
      IntAttribute(:final value) => value,
      DoubleAttribute(:final value) => value,
      BooleanAttribute(:final value) => value,
      StringAttribute(:final value) => value,
      StringListAttribute(:final value) => value,
      DoubleListAttribute(:final value) => value,
      IntListAttribute(:final value) => value,
      BooleanListAttribute(:final value) => value,
      InvalidAttribute() => null,
    },
};

final class _CallbackLogProcessor extends LDLogRecordProcessor {
  const _CallbackLogProcessor(this._onEmit);

  final void Function(LDLogRecord record) _onEmit;

  @override
  void onEmit(LDLogRecord record) => _onEmit(record);
}
