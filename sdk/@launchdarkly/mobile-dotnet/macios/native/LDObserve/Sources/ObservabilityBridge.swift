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
                crashReporting: .init(source: .none),
                instrumentation: .init(
                    urlSession: .disabled, // should be disabled because we use dotnet tracing for network requests
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
                    privacy: .init(
                        maskTextInputs: replay.maskTextInputs,
                        maskWebViews: replay.maskWebViews,
                        maskLabels: replay.maskLabels,
                        maskImages: replay.maskImages
                    )
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
