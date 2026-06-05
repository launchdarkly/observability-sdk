import 'package:flutter/widgets.dart';

import '../../options/observability_options.dart';
import '../../options/session_replay_options.dart';
import '../ld_observe_platform.dart';
import 'ld_observability_bridge.dart';
import 'masking/widget_masking_config.dart';
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
    // Per-widget Key/Type masking rules are resolved entirely on the Dart side
    // (they can't be serialized to native), so stash them where the capture
    // widget's MaskingPolicy can read them.
    final privacy = replay.privacy;
    activeWidgetMaskingConfig = WidgetMaskingConfig(
      maskTypes: privacy.maskWidgetTypes,
      maskKeys: privacy.maskWidgetKeys,
      unmaskTypes: privacy.unmaskWidgetTypes,
      unmaskKeys: privacy.unmaskWidgetKeys,
      ignoreTypes: privacy.ignoreWidgetTypes,
      ignoreKeys: privacy.ignoreWidgetKeys,
    );

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
