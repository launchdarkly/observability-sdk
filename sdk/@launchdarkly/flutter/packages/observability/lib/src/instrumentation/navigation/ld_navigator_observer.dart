import 'package:flutter/widgets.dart';

import '../../ld_observe.dart';
import 'route_path.dart';

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
/// By default the screen name is taken from `route.settings.name` minus its
/// query string; routes without a name are skipped. Provide
/// [screenNameExtractor] to customize this (for example to derive a name from
/// `route.settings.arguments` or to name otherwise-anonymous routes), or
/// `LDRoutePatterns.extractor` to report the route patterns of a router that
/// navigates by URL.
///
/// Observers are per-[Navigator] and a single instance cannot be shared, so an
/// app with nested navigators (tab shells, or a [Navigator] inside a page) needs
/// a separate instance for each. Routers that build their own navigator
/// (`MaterialApp.router` with go_router and friends) do not accept
/// `navigatorObservers`; pass the observer to the router's own observer list
/// instead. Navigation that does not change the route stack — switching tabs in
/// an `IndexedStack`, paging a `PageView` — is invisible to any observer and
/// needs an explicit [LDObserve.trackScreenView].
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

  /// The route behind the most recent [LDObserve.trackScreenView] call, used to
  /// suppress duplicates. See [didChangeTop].
  Route<dynamic>? _lastRecordedRoute;

  /// Default extractor: the route's [RouteSettings.name], without its query
  /// string or fragment.
  ///
  /// Routers that navigate by URL put the whole pushed URL in the name, query
  /// included, so `'/reset?token=abc123'` would become the reported screen —
  /// leaking a value into a name that identifies a screen, and giving that screen
  /// a fresh identity per value. The path alone is what names a screen.
  ///
  /// This does not collapse path parameters: `'/orders/42'` is still reported as
  /// itself, since only the app knows which segments are ids. Pass
  /// `LDRoutePatterns.extractor` for that.
  static String? defaultScreenNameExtractor(Route<dynamic> route) {
    final name = route.settings.name;
    return name == null ? null : routeNamePath(name);
  }

  @override
  void didChangeTop(Route<dynamic> topRoute, Route<dynamic>? previousTopRoute) {
    super.didChangeTop(topRoute, previousTopRoute);

    final name = _screenNameExtractor(topRoute);
    if (name == null || name.isEmpty) {
      return;
    }

    // A skipped route (typically an unnamed dialog or bottom sheet) leaves the
    // route underneath it as the last one recorded, so dismissing it makes that
    // route top again without it being a new navigation.
    if (identical(_lastRecordedRoute, topRoute)) {
      return;
    }

    LDObserve.trackScreenView(name, category: _category);
    _lastRecordedRoute = topRoute;
  }
}
