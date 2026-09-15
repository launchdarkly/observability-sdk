import 'package:flutter_test/flutter_test.dart';

import 'package:launchdarkly_flutter_observability/src/instrumentation/click/click_path.dart';

void main() {
  test('joins the ancestry outermost first', () {
    expect(
      ClickPath.build(['Scaffold', 'Column', 'Center', 'ElevatedButton']),
      equals('Scaffold/Column/Center/ElevatedButton'),
    );
  });

  test('suffixes the identifier when one is known', () {
    expect(
      ClickPath.build(['Scaffold', 'ElevatedButton'], id: 'checkout'),
      equals('Scaffold/ElevatedButton#checkout'),
    );
  });

  test('returns null when there is nothing to describe', () {
    expect(ClickPath.build([]), isNull);
    expect(ClickPath.build([], id: 'checkout'), isNull);
  });

  test('keeps the innermost segments, dropping generic outer scaffolding', () {
    final segments = [
      for (var i = 0; i < ClickPath.maxSegments + 5; i++) 'Widget$i',
    ];

    final path = ClickPath.build(segments)!;

    expect(path.split('/').length, equals(ClickPath.maxSegments));
    expect(path.split('/').first, equals('Widget5'));
    expect(path.split('/').last, equals('Widget14'));
  });

  test('holds the built path within the length budget', () {
    final segments = [for (var i = 0; i < 8; i++) 'W' * 60];

    final path = ClickPath.build(segments, id: 'checkout')!;

    expect(path.length, lessThanOrEqualTo(ClickPath.maxLength));
    // Trimmed from the outside in, so the target and its identifier survive.
    expect(path, endsWith('#checkout'));
  });

  test('truncates a single segment that exceeds the budget on its own', () {
    final path = ClickPath.build(['W' * (ClickPath.maxLength + 50)])!;

    expect(path.length, equals(ClickPath.maxLength));
  });
}
