import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';
import 'package:launchdarkly_flutter_observability/src/api/attribute.dart';
import 'package:launchdarkly_flutter_observability/src/otel/global_attributes.dart';

/// The global attribute store, without a tracer provider.
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
}
