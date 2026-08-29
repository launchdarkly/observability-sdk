import 'package:flutter/widgets.dart';

import '../../ld_observe.dart';

/// Signature for deriving a screen name from a [Route].
///
/// Return `null` to skip recording a screen view for the route (for example for
/// anonymous routes, dialogs, or popups that should not appear as navigations).
typedef LDScreenNameExtractor = String? Function(Route<dynamic> route);

/// A [NavigatorObserver] that reports Flutter route changes to LaunchDarkly
/// observability as screen views.
///
/// Flutter renders into a single native Activity/UIViewController, so the native
/// SDK's automatic screen detection never sees Flutter route changes. Attaching
/// this observer bridges that gap: each navigation is forwarded to
/// [LDObserve.trackScreenView], which emits a `screen_view` span and a Session
/// Replay `Navigate` timeline event.
///
/// Add it to your top-level navigator:
///
/// ```dart
/// MaterialApp(
///   navigatorObservers: [LDNavigatorObserver()],
///   // ...
/// );
/// ```
///
/// By default the screen name is taken from `route.settings.name`; routes
/// without a name are skipped. Provide [screenNameExtractor] to customize this
/// (for example to derive a name from `route.settings.arguments` or to name
/// otherwise-anonymous routes).
class LDNavigatorObserver extends NavigatorObserver {
  /// Creates an observer.
  ///
  /// [screenNameExtractor] customizes how a screen name is derived from a route;
  /// it defaults to [defaultScreenNameExtractor] (`route.settings.name`).
  /// [category] is an optional classifier attached to every recorded screen
  /// view (for example `"navigation"`).
  LDNavigatorObserver({
    LDScreenNameExtractor screenNameExtractor = defaultScreenNameExtractor,
    String? category,
  }) : _screenNameExtractor = screenNameExtractor,
       _category = category;

  final LDScreenNameExtractor _screenNameExtractor;
  final String? _category;

  /// Default extractor: uses the route's [RouteSettings.name].
  static String? defaultScreenNameExtractor(Route<dynamic> route) =>
      route.settings.name;

  void _record(Route<dynamic>? route) {
    if (route == null) {
      return;
    }
    final name = _screenNameExtractor(route);
    if (name == null || name.isEmpty) {
      return;
    }
    LDObserve.trackScreenView(name, category: _category);
  }

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    super.didPush(route, previousRoute);
    _record(route);
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    super.didReplace(newRoute: newRoute, oldRoute: oldRoute);
    _record(newRoute);
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    super.didPop(route, previousRoute);
    // After a pop the previously-underlying route becomes visible again, so it
    // is the screen the user navigates back to.
    _record(previousRoute);
  }
}
