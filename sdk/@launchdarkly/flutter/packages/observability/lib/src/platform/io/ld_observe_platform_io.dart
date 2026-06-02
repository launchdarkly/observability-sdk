import 'package:flutter/widgets.dart';

import '../../options/observability_options.dart';
import '../../options/session_replay_options.dart';
import '../ld_observe_platform.dart';
import 'ld_observability_bridge.dart';
import 'native_session_replay_capture.dart';

/// Native (iOS/Android) implementation. Boots the LaunchDarkly observability +
/// session replay stack over the pigeon bridge and captures Flutter-rendered
/// frames via [NativeSessionReplayCapture].
LDObservePlatform createLDObservePlatform() => _IoLDObservePlatform();

class _IoLDObservePlatform implements LDObservePlatform {
  @override
  Future<void> start({
    required String mobileKey,
    required ObservabilityOptions observability,
    required SessionReplayOptions replay,
  }) async {
    await LDNative.start(
      mobileKey: mobileKey,
      observability: observability,
      replay: replay,
    );
  }

  @override
  Widget wrapForCapture(Widget child) =>
      NativeSessionReplayCapture(child: child);

  @override
  String get nativeVersion => LDNative.current?.nativeVersion ?? '';
}
