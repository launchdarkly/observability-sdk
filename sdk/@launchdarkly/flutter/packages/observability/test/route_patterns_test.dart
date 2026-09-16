import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/navigation/ld_navigator_observer.dart';
import 'package:launchdarkly_flutter_observability/src/instrumentation/navigation/route_patterns.dart';

/// A route pushed under [name], the way a URL-based router reports it.
Route<void> _route(String? name) => PageRouteBuilder<void>(
  settings: RouteSettings(name: name),
  pageBuilder: (_, _, _) => const SizedBox.shrink(),
);

void main() {
  const patterns = <String>[
    '/',
    '/orders',
    '/orders/new',
    '/orders/:id',
    '/orders/:id/receipt',
    '/files/*',
  ];

  final extract = LDRoutePatterns.extractor(patterns);

  String? name(String? route, [LDScreenNameExtractor? extractor]) =>
      (extractor ?? extract)(_route(route));

  group('LDRoutePatterns', () {
    test('a parameterized route reports its pattern', () {
      expect(name('/orders/42'), '/orders/:id');
      expect(name('/orders/42/receipt'), '/orders/:id/receipt');
    });

    test('ids do not fragment the screen', () {
      expect(name('/orders/1'), name('/orders/2'));
    });

    test('a literal listed first wins over a pattern that also matches', () {
      expect(name('/orders/new'), '/orders/new');
    });

    test('the query string and fragment are dropped', () {
      expect(name('/orders/42?ref=email'), '/orders/:id');
      expect(name('/orders?sort=date#top'), '/orders');
    });

    test('a trailing slash is the same screen', () {
      expect(name('/orders/'), '/orders');
    });

    test('root matches the root pattern', () {
      expect(name('/'), '/');
    });

    test('a trailing * consumes the rest of the path', () {
      expect(name('/files/a/b/c.png'), '/files/*');
      // With nothing left to consume it is not a match, so the path stands.
      expect(name('/files'), '/files');
    });

    test('a partial match is not a match', () {
      expect(name('/orders/42/receipt/print'), '/orders/42/receipt/print');
    });

    test('an unlisted route keeps its path, without the query', () {
      expect(name('/settings/profile?tab=2'), '/settings/profile');
    });

    test('skipUnmatched turns the list into an allowlist', () {
      final strict = LDRoutePatterns.extractor(patterns, skipUnmatched: true);
      expect(name('/orders/42', strict), '/orders/:id');
      expect(name('/settings/profile', strict), isNull);
    });

    test('an unnamed route is skipped', () {
      expect(name(null), isNull);
      expect(name(''), isNull);
    });

    test('a name that is not a URL is left alone', () {
      // Route names need not be paths at all; `Uri.parse` would throw on this,
      // and punctuation in one is not a query string.
      expect(name('Checkout Page'), 'Checkout Page');
      expect(name('Delete this?'), 'Delete this?');
    });
  });
}
