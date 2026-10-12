import Foundation
import LaunchDarkly
import Common
import LaunchDarklyObservability
import LaunchDarklySessionReplay

internal func buildResourceAttributes(_ source: NSDictionary?) -> [String: AttributeValue] {
    guard let source = source as? [String: Any], !source.isEmpty else {
        return [:]
    }
    var result = [String: AttributeValue](minimumCapacity: source.count)
    for (key, value) in source {
        if let av = AttributeValue(value) {
            result[key] = av
        } else {
            result[key] = .string(String(describing: value))
        }
    }
    return result
}

internal func buildCustomHeaders(_ source: NSDictionary?) -> [String: String] {
    guard let source, source.count > 0 else {
        return [:]
    }
    var result = [String: String](minimumCapacity: source.count)
    for (key, value) in source {
        result[String(describing: key)] = (value as? String) ?? String(describing: value)
    }
    return result
}

@objc(ObservabilityBridge)
public final class ObservabilityBridge: NSObject {

    @objc public func version() -> String {
        return sdkVersion
    }

    @objc public func getSessionReplayHookProxy() -> SessionReplayHookProxy? {
        return LDReplay.shared.hookProxy
    }

    @objc public func start(mobileKey: String, 
                            observability: ObjcObservabilityOptions, 
                            replay: ObjcSessionReplayOptions,
                            observabilityVersion: String) {
        let config = { () -> LDConfig in
            var config = LDConfig(
                mobileKey: mobileKey,
                autoEnvAttributes: .enabled
            )
            config.startOnline = false

            let observabilityPlugin = Observability(options: .init(
                serviceName: observability.serviceName,
                serviceVersion: observability.serviceVersion,
                otlpEndpoint: observability.otlpEndpoint,
                backendUrl: observability.backendUrl,
                resourceAttributes: buildResourceAttributes(observability.attributes),
                customHeaders: buildCustomHeaders(observability.customHeaders),
                logsApiLevel: .init(rawValue: observability.logsApiLevel) ?? .none,
                crashReporting: .init(source: .none),
                instrumentation: .init(
                    urlSession: .disabled, // Network tracing happens on the .NET side via System.Net.Http activities.
                    userTaps: .enabled,
                    memory: .disabled,
                    memoryWarnings: .disabled,
                    cpu: .disabled,
                    launchTimes: observability.launchTimes ? .enabled : .disabled
                )
            ))
            observabilityPlugin.distroAttributes = [
                "telemetry.distro.name": "observability-maui-ios",
                "telemetry.distro.version": observabilityVersion
            ]

            config.plugins = [
                observabilityPlugin,
                SessionReplay(options: .init(
                    isEnabled: replay.isEnabled,
                    sampleRate: replay.sampleRate,
                    privacy: .init(
                        maskTextInputs: replay.maskTextInputs,
                        maskWebViews: replay.maskWebViews,
                        maskLabels: replay.maskLabels,
                        maskImages: replay.maskImages,
                        minimumAlpha: CGFloat(replay.minimumAlpha)
                    ),
                    frameRate: replay.frameRate,
                    scale: CGFloat(replay.scale),
                    imageQuality: CGFloat(replay.imageQuality)
                ))
            ]
            
            return config
        }()

        let context = { () -> LDContext in
            var contextBuilder = LDContextBuilder(
                key: "12345"
            )
            contextBuilder.kind("user")
            do {
                return try contextBuilder.build().get()
            } catch {
                abort()
            }
        }()
        
        let startTime = Date().timeIntervalSince1970.milliseconds
        LDClient.start(
            config: config,
            context: context,
            startWaitSeconds: 15.0,
            completion: { timeout in
                let end = Date().timeIntervalSince1970.milliseconds
                print("LDClient: started in \(end - startTime) ms")
                print("LDClient: started with timeout: \(timeout)")
            }
        )
    }
}
