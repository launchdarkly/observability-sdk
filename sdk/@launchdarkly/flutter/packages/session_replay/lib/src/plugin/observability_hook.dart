// Ported from
// sdk/@launchdarkly/mobile-dotnet/observability/observe/plugin/ObservabilityHook.cs
// and .../replay/plugin/SessionReplayHook.cs.

import 'package:launchdarkly_flutter_client_sdk/launchdarkly_flutter_client_sdk.dart';

/// Hook bundled with the native [ObservabilityPlugin]. Mirrors the C#
/// `ObservabilityHook`, which forwards evaluation and identify stages to the
/// native observability SDK.
///
/// The Flutter pigeon bridge does not yet expose the native hook proxy
/// (`NativeObservabilityHookExporter`), so the stages are currently
/// pass-throughs. The structure is kept so the native delegation can be wired
/// up once the bridge exposes it, exactly like the .NET implementation.
final class ObservabilityHook extends Hook {
  static const String _name = 'LaunchDarkly.Observability';

  final HookMetadata _metadata = const HookMetadata(name: _name);

  @override
  HookMetadata get metadata => _metadata;
}

/// Hook bundled with the native [ObservabilityPlugin] when session replay is
/// enabled. Mirrors the C# `SessionReplayHook`, which forwards the
/// `afterIdentify` stage to the native session replay SDK.
///
/// As with [ObservabilityHook], native delegation is not yet bridged over
/// pigeon, so the stages are pass-throughs.
final class SessionReplayHook extends Hook {
  static const String _name = 'LaunchDarkly.SessionReplay';

  final HookMetadata _metadata = const HookMetadata(name: _name);

  @override
  HookMetadata get metadata => _metadata;
}
