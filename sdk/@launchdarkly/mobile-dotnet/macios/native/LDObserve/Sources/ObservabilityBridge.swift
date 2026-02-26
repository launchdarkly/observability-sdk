//
//  ObservabilityBridge.swift
//  LDObserveBridge
//
//  Created by Andrey Belonogov on 1/22/26.
//


import Foundation
import LaunchDarkly
import Common
import LaunchDarklyObservability
import LaunchDarklySessionReplay

@objc(ObjcObservabilityOptions)
public final class ObjcObservabilityOptions: NSObject {
    @objc public var serviceName: String = ""
    @objc public var serviceVersion: String = ""
    @objc public var otlpEndpoint: String = ""
    @objc public var backendUrl: String = ""

    @objc public override init() {
        super.init()
    }
}

@objc(ObjcSessionReplayOptions)
public final class ObjcSessionReplayOptions: NSObject {
    @objc public var isEnabled: Bool = true
    @objc public var maskTextInputs: Bool = true
    @objc public var maskWebViews: Bool = false
    @objc public var maskLabels: Bool = false
    @objc public var maskImages: Bool = false

    @objc public override init() {
        super.init()
    }
}

@objc(ObjcEnvironmentMetadata)
public final class ObjcEnvironmentMetadata: NSObject {
    @objc public var credential: String = ""
    @objc public var sdkName: String = ""
    @objc public var sdkVersion: String = ""
    @objc public var applicationId: String = ""
    @objc public var applicationVersion: String = ""

    @objc public override init() {
        super.init()
    }
}

@objc(ObservabilityBridge)
public final class ObservabilityBridge: NSObject {

    @objc public func version() -> String {
        return sdkVersion
    }

    @objc public func getHookProxy() -> ObservabilityHookProxy? {
        return LDObserve.shared.hookProxy
    }

    @objc public func start(mobileKey: String, observability: ObjcObservabilityOptions, replay: ObjcSessionReplayOptions) {
        let config = { () -> LDConfig in
            var config = LDConfig(
                mobileKey: mobileKey,
                autoEnvAttributes: .enabled
            )
            config.startOnline = false

            config.plugins = [
                Observability(options: .init(
                    serviceName: observability.serviceName,
                    serviceVersion: observability.serviceVersion,
                    otlpEndpoint: observability.otlpEndpoint,
                    backendUrl: observability.backendUrl,
                    crashReporting: .init(source: .none)
                )),
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
