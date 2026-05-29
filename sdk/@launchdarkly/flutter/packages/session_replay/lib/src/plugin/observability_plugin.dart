// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/plugin/ObservabilityPlugin.cs.

import 'dart:async';

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

import '../ld_native.dart';
import '../observability_options.dart';
import '../session_replay_options.dart';
import 'observability_hook.dart';

/// Single LaunchDarkly plugin that wires up both observability and session
/// replay. When registered on an [LDClient] it boots the native stack once and
/// installs the relevant hooks.
///
/// Mirrors the C# `LaunchDarkly.Observability.ObservabilityPlugin`. It is not
/// exported from the package's public library to avoid colliding with the
/// Dart-side `ObservabilityPlugin` in `launchdarkly_flutter_observability`;
/// application code reaches it through [LDObserve.init].
final class ObservabilityPlugin extends Plugin {
  static const String _name = 'LaunchDarkly.Observability';

  final ObservabilityOptions observability;
  final SessionReplayOptions? replay;

  final PluginMetadata _metadata = const PluginMetadata(name: _name);

  ObservabilityPlugin(this.observability, {this.replay});

  @override
  PluginMetadata get metadata => _metadata;

  /// Boots the native stack with the credential the SDK was initialized with,
  /// mirroring `ObservabilityPlugin.Register` in the .NET bridge.
  @override
  void register(
    LDClient client,
    PluginEnvironmentMetadata environmentMetadata,
  ) {
    final replayOptions =
        replay ?? const SessionReplayOptions(isEnabled: false);

    // LDNative.start is asynchronous over the pigeon channel, whereas the
    // .NET LDNative.Start is synchronous. Fire-and-forget here so plugin
    // registration stays non-blocking.
    unawaited(
      LDNative.start(
        mobileKey: environmentMetadata.credential.value,
        observability: observability,
        replay: replayOptions,
      ),
    );
  }

  @override
  List<Hook> get hooks => [
    ObservabilityHook(),
    if (replay != null) SessionReplayHook(),
  ];
}
