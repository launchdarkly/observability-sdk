@_exported import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk

public struct Configuration {
    /**
     * The service name for the application.
     * @default 'my-swift-app'
     */
    public let serviceName: String
    /**
     * The endpoint URL for the OTLP exporter.
     * @default 'https://otel.observability.app.launchdarkly.com:4318'
     */
    public let otlpEndpoint: String
    /**
     * The service version for the application.
     * @default '1.0.0'
     */
    public let serviceVersion: String
    /**
     * Additional resource attributes to include in telemetry data.
     */
    public private(set) var resourceAttributes: [String: AttributeValue]
    /**
     * Custom headers to include with OTLP exports.
     */
    public let customHeaders: [(String, String)]
    /**
     * Session timeout in seconds.
     * @default 30 * 60  (30 minutes)
     */
    public let sessionTimeout: TimeInterval
    /**
     * Debug mode - enables additional logging.
     * @default false
     */
    public let isDebug: Bool
    
    /**
     * Whether errors tracking is disabled.
     */
    public let isErrorTrackingDisabled: Bool
    
    /**
     * Whether logs are disabled.
     */
    public let isLogsDisabled: Bool
    
    /**
     * Whether traces are disabled.
     */
    public let isTracesDisabled: Bool
    
    /**
     * Whether metrics are disabled.
     */
    public let isMetricsDisabled: Bool
    
    public init(
        serviceName: String = "App",
        otlpEndpoint: String = "https://otel.observability.app.launchdarkly.com:4318",
        serviceVersion: String = "1.0.0",
        resourceAttributes: [String: AttributeValue] = [:],
        customHeaders: [(String, String)] = [],
        sessionTimeout: TimeInterval = 30 * 60,
        isDebug: Bool = false,
        isErrorTrackingDisabled: Bool = false,
        isLogsDisabled: Bool = false,
        isTracesDisabled: Bool = false,
        isMetricsDisabled: Bool = false
    ) {
        self.serviceName = serviceName
        self.otlpEndpoint = otlpEndpoint
        self.serviceVersion = serviceVersion
        self.resourceAttributes = resourceAttributes
        self.customHeaders = customHeaders
        self.sessionTimeout = sessionTimeout
        self.isDebug = isDebug
        self.isErrorTrackingDisabled = isErrorTrackingDisabled
        self.isLogsDisabled = isLogsDisabled
        self.isTracesDisabled = isTracesDisabled
        self.isMetricsDisabled = isMetricsDisabled
    }
    
    public func updateSessionId(_ sessionId: String) -> Self {
        var updatedSelf = self
        var resourceAttributes = self.resourceAttributes
        resourceAttributes["session.id"] = .string(sessionId)
        updatedSelf.resourceAttributes = resourceAttributes
        
        return updatedSelf
    }
}
