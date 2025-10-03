import 'package:flutter/material.dart';
import 'package:launchdarkly_flutter_observability/launchdarkly_flutter_observability.dart';

import '../observe.dart';

class TrackingNavigatorObserver extends NavigatorObserver {
  void _recordNavigation(
    String action,
    Route<dynamic> route,
    Route<dynamic>? previous,
  ) {
    final span = Observe.startSpan('device.app.navigation');
    span.setAttribute(
      'route.name',
      StringAttribute(route.settings.name ?? 'unknown'),
    );
    span.setAttribute('navigation.action', StringAttribute(action));
    if (previous != null) {
      span.setAttribute(
        'route.previous.name',
        StringAttribute(previous.settings.name ?? 'unknown'),
      );
    }
    span.end();
  }

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    _recordNavigation('push', route, previousRoute);
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    _recordNavigation('pop', route, previousRoute);
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    if (newRoute != null) {
      _recordNavigation('replace', newRoute, oldRoute);
    }
  }
}
