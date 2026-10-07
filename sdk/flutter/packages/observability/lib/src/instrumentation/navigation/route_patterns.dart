import 'ld_navigator_observer.dart';
import 'route_path.dart';

/// Builds an [LDScreenNameExtractor] that reports the route *pattern* a
/// navigation matched instead of the URL it was pushed with.
///
/// Routers that navigate by URL — GetX, go_router, Beamer, and anything else
/// pushing `'/orders/42'` — put the concrete path in `route.settings.name`, so
/// every order becomes its own screen. That splits one screen into thousands and
/// puts an order id in a name that is shown in the UI. Naming the patterns your
/// app registers collapses them back:
///
/// ```dart
/// LDNavigatorObserver(
///   screenNameExtractor: LDRoutePatterns.extractor(const [
///     '/orders',
///     '/orders/:id',
///     '/orders/:id/receipt',
///   ]),
/// )
/// ```
///
/// A tap through that flow then reports `/orders`, `/orders/:id`, and
/// `/orders/:id/receipt`, whatever ids were involved.
///
/// Pattern syntax is the one those routers already use: a `:name` segment
/// matches exactly one segment, and a trailing `*` matches the rest of the path.
/// The query string and fragment are always dropped, so `'/search?q=shoes'`
/// cannot fragment a screen either.
abstract final class LDRoutePatterns {
  /// An extractor matching against [patterns], most specific match first.
  ///
  /// Patterns are compared in the order given and the first match wins, so list
  /// a literal ahead of a pattern that would also match it (`'/orders/new'`
  /// before `'/orders/:id'`).
  ///
  /// A route whose name matches nothing is reported by its path with the query
  /// dropped, so a screen you forgot to list still shows up. Pass
  /// `skipUnmatched: true` to record only the patterns you named instead, which
  /// turns the list into an allowlist.
  static LDScreenNameExtractor extractor(
    Iterable<String> patterns, {
    bool skipUnmatched = false,
  }) {
    final compiled = [
      for (final pattern in patterns) _CompiledPattern(pattern),
    ];
    return (route) {
      final name = route.settings.name;
      if (name == null || name.isEmpty) {
        return null;
      }
      final path = routeNamePath(name);
      final segments = _segments(path);
      for (final pattern in compiled) {
        if (pattern.matches(segments)) {
          return pattern.source;
        }
      }
      return skipUnmatched ? null : path;
    };
  }

  static List<String> _segments(String path) =>
      path.split('/').where((segment) => segment.isNotEmpty).toList();
}

/// One pattern, pre-split so matching a navigation costs no parsing.
class _CompiledPattern {
  _CompiledPattern(this.source)
    : _segments = LDRoutePatterns._segments(routeNamePath(source));

  /// The pattern as written, which is what gets reported.
  final String source;

  final List<String> _segments;

  bool matches(List<String> path) {
    for (var i = 0; i < _segments.length; i++) {
      final segment = _segments[i];
      // A trailing `*` stands for the rest of the path, so it needs at least one
      // segment left to consume.
      if (segment == '*') {
        return i < path.length;
      }
      if (i >= path.length) {
        return false;
      }
      // `:id` matches any single segment; anything else must match literally.
      if (!segment.startsWith(':') && segment != path[i]) {
        return false;
      }
    }
    return _segments.length == path.length;
  }
}
