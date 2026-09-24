import 'package:flutter/widgets.dart';

import '../options/observability_options.dart';
import '../options/session_replay_options.dart';

// Selects the platform implementation at compile time. The native (io)
// implementation pulls in the pigeon bridge; the web implementation has no
// session replay. Neither is compiled into the other's build, so the native
// bridge never leaks into a web build.
import 'ld_observe_platform_stub.dart'
    if (dart.library.io) 'io/ld_observe_platform_io.dart'
    if (dart.library.js_interop) 'web/ld_observe_platform_web.dart';

/// Platform abstraction for booting observability/session replay and wrapping
/// the widget tree for capture. Concrete implementations live under
/// `platform/io` (native, pigeon) and `platform/web`.
abstract interface class LDObservePlatform {
  /// The platform implementation selected for the current build target.
  static final LDObservePlatform instance = createLDObservePlatform();

  /// Boots the native observability and session replay stack. A no-op where
  /// there is no native stack (web).
  Future<void> start({
    required String mobileKey,
    required ObservabilityOptions observability,
    required SessionReplayOptions replay,
  });

  /// Stops whatever [start] booted that can be stopped. On native that is
  /// session replay only: the native observability SDKs have no teardown.
  Future<void> shutdown();

  /// Wraps [child] so it can be captured for session replay.
  ///
  /// Native: returns a screenshot-capturing boundary. Web: returns [child]
  /// unchanged, because session replay is not supported on web.
  Widget wrapForCapture(Widget child);

  /// The native bridge version reported during startup, or an empty string
  /// when there is no native bridge (e.g. web).
  String get nativeVersion;
}
