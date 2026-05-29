// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/api/LDObserve.cs.

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import 'ld_native.dart';
import 'observability_options.dart';
import 'plugin/observability_plugin.dart';
import 'session_replay_options.dart';

/// Static facade for booting LaunchDarkly observability (and optional session
/// replay) from Flutter. Mirrors the C# `LaunchDarkly.Observability.LDObserve`
/// facade, which exposes two `Init` entry points.
///
/// Dart does not support method overloading, so the two variants are split
/// into [LDObserve.init] (LaunchDarkly client backed) and
/// [LDObserve.initStandalone] (standalone, no client). The differentiator is
/// the first argument — an [LDClient] versus a mobile key string — matching
/// the two `Init(client, ...)` / `Init(mobileKey, ...)` overloads in MAUI.
final class LDObserve {
  LDObserve._();

  /// Boots LaunchDarkly observability (and optional session replay) by
  /// registering the [ObservabilityPlugin] on an already-constructed
  /// [LDClient].
  ///
  /// Mirrors `LDObserve.Init(LdClient client, ...)` in the .NET bridge: the
  /// plugin's `register` boots the native stack using the SDK credential and
  /// installs the observability + session replay hooks.
  ///
  /// When [replay] is omitted, session replay is not wired up.
  static void init(
    LDClient client, {
    required ObservabilityOptions observability,
    SessionReplayOptions? replay,
  }) {
    client.registerPlugin(ObservabilityPlugin(observability, replay: replay));
  }

  /// Boots the native LaunchDarkly observability (and optional session replay)
  /// stack standalone, without a LaunchDarkly client.
  ///
  /// Mirrors `LDObserve.Init(string mobileKey, ...)` in the .NET bridge: it
  /// starts the native stack directly and then wires up this facade so the
  /// recording APIs work without an [LDClient].
  ///
  /// When [replay] is omitted, session replay is started disabled.
  static Future<void> initStandalone(
    String mobileKey, {
    required ObservabilityOptions observability,
    SessionReplayOptions? replay,
  }) async {
    await LDNative.start(
      mobileKey: mobileKey,
      observability: observability,
      replay: replay ?? const SessionReplayOptions(isEnabled: false),
    );
  }

  /// The native observability bridge version reported during startup, or an
  /// empty string before initialization has completed.
  static String get nativeVersion => LDNative.current?.nativeVersion ?? '';
}
