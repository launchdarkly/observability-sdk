// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/bridge/LDNative.cs.

import 'messages.g.dart';
import 'observability_options.dart';
import 'session_replay_options.dart';

/// The version of this Flutter package, surfaced in the resource attributes
/// the native bridge attaches as `telemetry.distro.version`. Kept in sync
/// with `pubspec.yaml` by hand for now.
const String _packageVersion = '0.0.0';

/// Internal entry point for starting the LaunchDarkly observability +
/// session replay native stack from Flutter. Mirrors the C#
/// `LaunchDarkly.SessionReplay.LDNative` class in
/// `sdk/@launchdarkly/mobile-dotnet/observability/bridge/LDNative.cs`.
///
/// This type is intentionally not exported from the package's public library.
/// Application code boots the native stack through [LDObserve] instead.
class LDNative {
  /// The most recently started [LDNative] instance. Mirrors the
  /// `internal static LDNative? Current` field in the .NET bridge.
  static LDNative? current;

  final ObservabilityOptions observability;
  final SessionReplayOptions replay;
  String nativeVersion;

  LDNative._({required this.observability, required this.replay})
    : nativeVersion = '';

  /// Boots the native LaunchDarkly observability + session replay stack on
  /// the current platform. Mirrors `LDNative.Start(...)` in the .NET bridge.
  static Future<LDNative> start({
    required String mobileKey,
    required ObservabilityOptions observability,
    required SessionReplayOptions replay,
  }) async {
    final ldNative = LDNative._(observability: observability, replay: replay);
    current = ldNative;
    final api = LDNativeApi();
    final result = await api.start(
      mobileKey,
      observability.toWire(),
      replay.toWire(),
      _packageVersion,
    );
    ldNative.nativeVersion = result.nativeVersion ?? '';
    return ldNative;
  }
}
