import Flutter

/// Flutter plugin entry point for `launchdarkly_flutter_session_replay`.
/// Wires the pigeon-generated `LDNativeApi` host API to `LDNativeApiImpl`,
/// which boots the LaunchDarkly observability + session replay native stack —
/// a port of `LDNative.cs` / `ObservabilityBridge.swift` from
/// `sdk/@launchdarkly/mobile-dotnet`.
public class LaunchdarklyFlutterSessionReplayPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let captureChannel = FlutterMethodChannel(
            name: "launchdarkly_flutter_session_replay/capture",
            binaryMessenger: registrar.messenger()
        )
        let api = LDNativeApiImpl(captureChannel: captureChannel)
        LDNativeApiSetup.setUp(binaryMessenger: registrar.messenger(), api: api)
    }
}
