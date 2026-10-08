import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/options/observability_options.dart';
import 'package:launchdarkly_flutter_observability/src/otel/symbols_id.dart';
import 'package:launchdarkly_flutter_observability/src/plugin/ld_observe_plugin.dart';

void main() {
  group('applySymbolsId (native-init injection)', () {
    const buildId = '0f8a1b2c3d4e5f60718293a4b5c6d7e8';

    test('adds symbols_id to native attributes when none are set', () {
      final result = applySymbolsId(const ObservabilityOptions(), buildId);

      expect(result.attributes, {symbolsIdAttributeKey: buildId});
    });

    test('preserves existing attributes while adding symbols_id', () {
      const options = ObservabilityOptions(attributes: {'team': 'checkout'});

      final result = applySymbolsId(options, buildId);

      expect(result.attributes, {
        'team': 'checkout',
        symbolsIdAttributeKey: buildId,
      });
    });

    test('does not clobber an app-provided symbols_id', () {
      const options = ObservabilityOptions(
        attributes: {symbolsIdAttributeKey: 'app-set'},
      );

      final result = applySymbolsId(options, buildId);

      expect(result.attributes?[symbolsIdAttributeKey], 'app-set');
    });

    test('returns the options unchanged when there is no symbols_id', () {
      const options = ObservabilityOptions(attributes: {'team': 'checkout'});

      final result = applySymbolsId(options, null);

      expect(identical(result, options), isTrue);
    });
  });
}
