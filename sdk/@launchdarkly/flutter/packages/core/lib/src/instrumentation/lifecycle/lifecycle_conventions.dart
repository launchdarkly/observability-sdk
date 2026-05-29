import 'dart:ui';
import '../../api/attribute.dart';

const _lifecycleSpanName = "device.app.lifecycle";
const _flutterAppState = "flutter.app.state";

enum LifecycleState {
  detached('detached'),
  resumed('resumed'),
  inactive('inactive'),
  hidden('hidden'),
  paused('paused');

  final String stringValue;

  const LifecycleState(String value) : stringValue = value;

  @override
  String toString() {
    return stringValue;
  }

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
