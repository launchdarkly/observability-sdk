import Flutter
import Foundation
import LaunchDarkly
import LaunchDarklyObservability
import LaunchDarklySessionReplay

/// Pigeon `LDNativeApi` implementation. Ported (line-for-line where it makes
/// sense) from `ObservabilityBridge.swift` in
/// `sdk/@launchdarkly/mobile-dotnet/macios/native/LDObserve/Sources/ObservabilityBridge.swift`,
/// with the Pigeon-generated wire DTOs taking the place of the .NET-side
/// `ObjcObservabilityOptions` / `ObjcSessionReplayOptions` types.
final class LDNativeApiImpl: NSObject, LDNativeApi {
    private let captureChannel: FlutterMethodChannel

    init(captureChannel: FlutterMethodChannel) {
        self.captureChannel = captureChannel
        super.init()
    }

    func start(
        mobileKey: String,
        observability: LDObservabilityOptions,
        replay: LDSessionReplayOptions,
        observabilityVersion: String,
        completion: @escaping (Result<LDStartResult, Error>) -> Void
    ) {
        do {
            let config = try makeConfig(
                mobileKey: mobileKey,
                observability: observability,
                replay: replay,
                observabilityVersion: observabilityVersion
            )

            let context = try makeAnonymousContext()

            LDClient.start(
                config: config,
                context: context,
                startWaitSeconds: 15.0,
                completion: { _ in }
            )

            completion(.success(LDStartResult(nativeVersion: observabilityVersion)))
        } catch {
            completion(.failure(error))
        }
    }

    private func makeConfig(
        mobileKey: String,
        observability: LDObservabilityOptions,
        replay: LDSessionReplayOptions,
        observabilityVersion: String
    ) throws -> LDConfig {
        var config = LDConfig(
            mobileKey: mobileKey,
            autoEnvAttributes: .enabled
        )
        config.startOnline = false

        let observabilityPlugin = Observability(options: .init(
            serviceName: observability.serviceName ?? Defaults.serviceName,
            serviceVersion: observability.serviceVersion ?? Defaults.serviceVersion,
            otlpEndpoint: observability.otlpEndpoint ?? Defaults.otlpEndpoint,
            backendUrl: observability.backendUrl ?? Defaults.backendUrl,
            resourceAttributes: buildResourceAttributes(observability.attributes),
            crashReporting: .init(source: .none),
            instrumentation: .init(
                urlSession: .disabled,
                userTaps: .enabled,
                memory: .disabled,
                memoryWarnings: .disabled,
                cpu: .disabled,
                launchTimes: (observability.instrumentation?.launchTimes ?? true) ? .enabled : .disabled
            )
        ))
        observabilityPlugin.distroAttributes = [
            "telemetry.distro.name": "observability-flutter-ios",
            "telemetry.distro.version": observabilityVersion,
        ]

        let sessionReplayOptions = makeSessionReplayOptions(replay: replay)
        let privacy = replay.privacy
        let maskTextInputs = privacy?.maskTextInputs ?? true
        let flutterCaptureService = FlutterImageCaptureService(
            channel: captureChannel,
            maskTextInputs: maskTextInputs
        )
        config.plugins = [
            observabilityPlugin,
            SessionReplay(
                options: sessionReplayOptions,
                imageCaptureService: flutterCaptureService
            ),
        ]

        return config
    }

    private func makeSessionReplayOptions(
        replay: LDSessionReplayOptions
    ) -> LaunchDarklySessionReplay.SessionReplayOptions {
        let privacy = replay.privacy

        return LaunchDarklySessionReplay.SessionReplayOptions(
            isEnabled: replay.isEnabled ?? true,
            privacy: .init(
                maskTextInputs: privacy?.maskTextInputs ?? true,
                maskWebViews: privacy?.maskWebViews ?? false,
                maskLabels: privacy?.maskLabels ?? false,
                maskImages: privacy?.maskImages ?? false
            )
        )
    }

    private func makeAnonymousContext() throws -> LDContext {
        var contextBuilder = LDContextBuilder(key: "flutter-user-key")
        contextBuilder.kind("user")
        switch contextBuilder.build() {
        case .success(let context):
            return context
        case .failure(let error):
            throw error
        }
    }

    private func buildResourceAttributes(_ source: [String: Any?]?) -> [String: AttributeValue] {
        guard let source = source, !source.isEmpty else { return [:] }
        var result = [String: AttributeValue](minimumCapacity: source.count)
        for (key, value) in source {
            guard let value = value else { continue }
            if let av = AttributeValue(value) {
                result[key] = av
            } else {
                result[key] = .string(String(describing: value))
            }
        }
        return result
    }

    private enum Defaults {
        static let serviceName = "observability-flutter"
        static let serviceVersion = "0.1.0"
        static let otlpEndpoint = "https://otel.observability.app.launchdarkly.com:4318"
        static let backendUrl = "https://pub.observability.app.launchdarkly.com"
    }
}
