import 'package:flutter/widgets.dart';

import '../../options/observability_options.dart';
import '../../options/session_replay_options.dart';
import '../ld_observe_platform.dart';
import 'web_session_replay_capture.dart';

/// Web implementation.
///
/// Observability (traces/logs) is handled by the cross-platform Dart
/// OpenTelemetry pipeline, and session replay is not supported on web, so
/// there is no platform stack to start or stop and the capture wrapper is a
/// pass-through.
LDObservePlatform createLDObservePlatform() => _WebLDObservePlatform();

class _WebLDObservePlatform implements LDObservePlatform {
  @override
  Future<void> start({
    required String mobileKey,
    required ObservabilityOptions observability,
    required SessionReplayOptions replay,
  }) async {}

  @override
  Future<void> shutdown() async {}

  @override
  Widget wrapForCapture(Widget child) => WebSessionReplayCapture(child: child);

  @override
  String get nativeVersion => '';
}
