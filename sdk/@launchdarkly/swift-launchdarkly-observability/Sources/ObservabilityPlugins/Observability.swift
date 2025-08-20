import LaunchDarkly
import OpenTelemetryApi
import OpenTelemetrySdk
import ResourceExtension
import Client
import LaunchDarklyObservability

public final class Observability: Plugin {
    public init() {}
    
    public func getMetadata() -> PluginMetadata {
        guard case let .string(serviceName) = ObservabilityClient.defaultResource().attributes["service.name"], !serviceName.isEmpty else {
            return .init(name: "'@launchdarkly/observability-ios'")
        }
        return .init(name: serviceName)
    }
    
    public func register(client: LDClient, metadata: EnvironmentMetadata) {
        let sdkKey = metadata.credential
        
        
        var resourceAttributes = ObservabilityClient.defaultResource().attributes
        resourceAttributes["launchdarkly.sdk.version"] = .string(String(format: "%@/%@", metadata.sdkMetadata.name, metadata.sdkMetadata.version))
        resourceAttributes["highlight.project_id"] = .string(sdkKey)
        
        let configuration = Configuration(
//            otlpEndpoint: "http://127.0.0.1:4318",
            resourceAttributes: resourceAttributes,
            customHeaders: [("x-launchdarkly-project", sdkKey)]
        )
        let client = ObservabilityClient(
            configuration: configuration,
            sdkKey: sdkKey
        )
        
        LDObserve.shared.set(client: client)
    }
    
    public func getHooks(metadata: EnvironmentMetadata) -> [any Hook] {
        [
            EvalTracingHook(withSpans: true, withValue: true)
        ]
    }
}
