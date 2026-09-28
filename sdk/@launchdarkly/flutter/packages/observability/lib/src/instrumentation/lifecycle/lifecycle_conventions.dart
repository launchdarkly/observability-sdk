import 'dart:ui';
import '../../api/attribute.dart';

const _lifecycleSpanName = "device.app.lifecycle";
const _flutterAppState = "flutter.app.state";

/// Application lifecycle states reported in the `flutter.app.state` attribute.
enum LifecycleState {
  /// The engine is running without a view attached.
  detached('detached'),

  /// The application is visible and has input focus.
  resumed('resumed'),

  /// The application is visible but does not have input focus.
  inactive('inactive'),

  /// The application is not visible.
  hidden('hidden'),

  /// The application is not visible and not responding to user input.
  paused('paused');

  /// The value reported for this state in the `flutter.app.state` attribute.
  final String stringValue;

  const LifecycleState(String value) : stringValue = value;

  @override
  String toString() {
    return stringValue;
  }

  /// Maps a Flutter [AppLifecycleState] to the corresponding [LifecycleState].
  static LifecycleState fromAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.detached:
        return LifecycleState.detached;
      case AppLifecycleState.resumed:
        return LifecycleState.resumed;
      case AppLifecycleState.inactive:
        return LifecycleState.inactive;
      case AppLifecycleState.hidden:
        return LifecycleState.hidden;
      case AppLifecycleState.paused:
        return LifecycleState.paused;
    }
  }
}

/// LaunchDarkly specific lifecycle convention inspired by the otel mobile
/// events semantic convention.
final class LifecycleConventions {
  /// Returns the span attributes for a lifecycle transition to [state], namely
  /// `flutter.app.state`.
  static Map<String, Attribute> getAttributes({
    required AppLifecycleState state,
  }) {
    return {
      _flutterAppState: StringAttribute(
        LifecycleState.fromAppLifecycleState(state).toString(),
      ),
    };
  }

  /// The name to use for the span.
  static const spanName = _lifecycleSpanName;
}
