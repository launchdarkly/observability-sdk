import 'package:flutter/widgets.dart';

import '../../options/observability_options.dart';
import '../../options/session_replay_options.dart';
import '../ld_observe_platform.dart';
import 'web_session_replay_capture.dart';

/// Web implementation.
///
/// Observability (traces/logs) is handled by the cross-platform Dart
/// OpenTelemetry pipeline, so this implementation is only responsible for web
/// session replay.
///
/// TODO(observability): wire [start] to the LaunchDarkly browser session
/// replay JS SDK via `dart:js_interop` / `package:web`. For now it is a
/// documented no-op so the package compiles and runs on web; the capture
/// wrapper is a pass-through because the browser SR records the DOM directly.
LDObservePlatform createLDObservePlatform() => _WebLDObservePlatform();

class _WebLDObservePlatform implements LDObservePlatform {
  @override
  Future<void> start({
    required String mobileKey,
    required ObservabilityOptions observability,
    required SessionReplayOptions replay,
  }) async {
    // No-op until the browser session replay SDK is bridged.
  }

  @override
  Widget wrapForCapture(Widget child) => WebSessionReplayCapture(child: child);

  @override
  String get nativeVersion => '';
}
