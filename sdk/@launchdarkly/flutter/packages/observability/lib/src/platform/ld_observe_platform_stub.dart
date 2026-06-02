import 'package:flutter/widgets.dart';

import '../options/observability_options.dart';
import '../options/session_replay_options.dart';
import 'ld_observe_platform.dart';

/// Fallback used when neither `dart:io` nor `dart:js_interop` is available.
/// Session replay is unsupported on such targets, so [start] is a no-op and
/// capture is a pass-through.
LDObservePlatform createLDObservePlatform() => _StubLDObservePlatform();

class _StubLDObservePlatform implements LDObservePlatform {
  @override
  Future<void> start({
    required String mobileKey,
    required ObservabilityOptions observability,
    required SessionReplayOptions replay,
  }) async {}

  @override
  Widget wrapForCapture(Widget child) => child;

  @override
  String get nativeVersion => '';
}
