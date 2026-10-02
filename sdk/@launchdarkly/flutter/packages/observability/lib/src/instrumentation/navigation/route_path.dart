/// The path part of a route name: everything before the query string and
/// fragment, with a trailing slash removed.
///
/// Applied to every reported screen name, because a route name is shown in the
/// UI and attached to spans, and a query string is the one part of it holding
/// values rather than structure: `'/reset?token=abc123'` and
/// `'/search?q=<whatever was typed>'` put a secret and a user's words into a
/// name meant to identify a screen, and give that screen a new identity per
/// value on top of it.
///
/// Only names that look like paths are treated as URLs. A route name is just a
/// string and an app is free to use a sentence — `'Delete this?'` is a
/// legitimate name for a confirmation route — so anything not starting with `/`
/// is returned untouched rather than truncated at its punctuation.
///
/// Split by hand rather than with `Uri.parse`, which throws on names that are
/// not URLs at all.
String routeNamePath(String name) {
  if (!name.startsWith('/')) {
    return name;
  }

  var end = name.length;
  for (var i = 0; i < name.length; i++) {
    final char = name[i];
    if (char == '?' || char == '#') {
      end = i;
      break;
    }
  }
  var path = name.substring(0, end);

  // `/orders/` and `/orders` are the same screen. The root stays `/`.
  if (path.length > 1 && path.endsWith('/')) {
    path = path.substring(0, path.length - 1);
  }
  return path;
}
