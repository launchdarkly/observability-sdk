import Foundation

@objc(ObjcObservabilityOptions)
public final class ObjcObservabilityOptions: NSObject {
    @objc public var serviceName: String = ""
    @objc public var serviceVersion: String = ""
    @objc public var otlpEndpoint: String = ""
    @objc public var backendUrl: String = ""
    @objc public var attributes: NSDictionary?
    // Not consumed yet — network tracing is handled on the .NET side. Kept for future iOS-native URL session instrumentation.
    @objc public var networkRequests: Bool = true
    @objc public var launchTimes: Bool = true
    @objc public var customHeaders: NSDictionary?
    /// OpenTelemetry severity number; values above `fatal4` (24) disable log export.
    @objc public var logsApiLevel: Int = 9

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
    @objc public var minimumAlpha: Double = 0.02
    @objc public var sampleRate: Double = 1.0
    @objc public var frameRate: Double = 1.0
    @objc public var scale: Double = 1.0
    @objc public var imageQuality: Double = 0.3

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
