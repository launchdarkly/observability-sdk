/// Builds the `event.xpath` value for a click: the tapped widget's ancestry,
/// the mobile analogue of a web CSS selector path.
///
/// The taxonomy reserves `event.xpath` as "XPath (web) / view path (mobile)", so
/// the shape aims at the same use — telling two same-named targets apart and
/// grouping taps by where they sit in the UI:
///
/// ```
/// Scaffold/Column/Center/ElevatedButton#checkout
/// ```
///
/// Two caps keep it useful and bounded. Only the innermost [maxSegments]
/// segments are kept, because a Flutter tree's upper reaches are the same
/// `MaterialApp`/`Navigator`/`Overlay` scaffolding on every screen and say
/// nothing about the tap, while the segments nearest the target are what
/// distinguish it. The joined result is then held under [maxLength], dropping
/// whole segments from the outside in, so one deeply-generic widget name cannot
/// blow up the attribute.
///
/// Segment names are best-effort for widgets the click registry does not
/// recognize: they come from the runtime type, which `--obfuscate` mangles. The
/// recognized target's own segment is a string literal and stays readable, as do
/// `event.tag` and `event.id`, so grouping on those is stable even when the path
/// is not. Private types (leading underscore) are dropped — they are framework
/// internals in any build, and their names are the first thing obfuscation
/// destroys.
abstract final class ClickPath {
  /// Innermost segments kept. Matches the depth Sentry keeps for its `path`.
  static const maxSegments = 10;

  /// Upper bound on the built path, in characters.
  static const maxLength = 256;

  /// Joins [segments] — outermost first, the target last — into a path,
  /// appending `#`[id] when a stable identifier is known.
  ///
  /// Returns null when nothing is left to describe, so the attribute is omitted
  /// rather than sent empty.
  static String? build(List<String> segments, {String? id}) {
    var kept = segments.length > maxSegments
        ? segments.sublist(segments.length - maxSegments)
        : segments;
    if (kept.isEmpty) {
      return null;
    }

    final suffix = id == null ? '' : '#$id';

    // Trim from the outside in: the outermost segment is the least specific, so
    // it is the cheapest thing to give up to fit the budget.
    var length = suffix.length + _joinedLength(kept);
    var start = 0;
    while (length > maxLength && start < kept.length - 1) {
      // Segment plus the '/' that followed it.
      length -= kept[start].length + 1;
      start++;
    }
    if (start > 0) {
      kept = kept.sublist(start);
    }

    final path = '${kept.join('/')}$suffix';
    // A single segment can still exceed the budget on its own.
    return path.length > maxLength ? path.substring(0, maxLength) : path;
  }

  static int _joinedLength(List<String> segments) {
    var length = segments.length - 1; // separators
    for (final segment in segments) {
      length += segment.length;
    }
    return length;
  }
}
