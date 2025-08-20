@_exported import Foundation
@preconcurrency import OpenTelemetryApi
import OpenTelemetrySdk
import StdoutExporter
import OpenTelemetryProtocolExporterHttp



public struct TracerFacade {
    private let configuration: Configuration
    public let tracer: Tracer
    private let tracerProvider: TracerProviderSdk
    
    public var currentSpan: Span? {
        OpenTelemetry.instance.contextProvider.activeSpan
    }
    
    public init(configuration: Configuration) {
        func buildSpanExporter(using configuration: Configuration) -> SpanExporter {
            var spanExporters = [any SpanExporter]()
            
            if let httpExporter = buildHttpExporter(using: configuration) {
                spanExporters.append(httpExporter)
            }
            
            if configuration.isDebug {
                spanExporters.append(
                    StdoutSpanExporter(isDebug: configuration.isDebug)
                )
            }
            
            return MultiSpanExporter(spanExporters: spanExporters)
        }
        
        func buildHttpExporter(using configuration: Configuration) -> SpanExporter? {
            guard let url = URL(string: configuration.otlpEndpoint) else {
                print("Http exporter will not be available, due to invalid URL in the otlpEndpoint")
                return nil
            }
            
            let tracesUrl = url.appending(path: HttpExporterPath.traces)
            return OtlpHttpTraceExporter(
                endpoint: tracesUrl,
                envVarHeaders: configuration.customHeaders
            )
        }
        
        func buildTracerProvider(
            using resource: Resource,
            spanProcessor: SpanProcessor
        ) -> TracerProviderSdk {
            TracerProviderBuilder()
                .add(spanProcessor: spanProcessor)
                .with(resource: resource)
                .build()
        }
        self.configuration = configuration
        let tracerProvider = buildTracerProvider(
            using: Resource(attributes: configuration.resourceAttributes),
            spanProcessor: BatchSpanProcessor(
                spanExporter: buildSpanExporter(using: configuration),
                scheduleDelay: 1,
                exportTimeout: 5,
                maxQueueSize: 100,
                maxExportBatchSize: 10
            )
        )
        OpenTelemetry.registerTracerProvider(
            tracerProvider: tracerProvider
        )
        
        self.tracerProvider = tracerProvider
        self.tracer = OpenTelemetry.instance.tracerProvider.get(
            instrumentationName: configuration.serviceName,
            instrumentationVersion: configuration.serviceVersion
        )
    }
    
    // MARK: - Public API
    public func spanBuilder(spanName: String) -> SpanBuilder {
        tracer
            .spanBuilder(spanName: spanName)
            
    }
    
    public func flush(timeout: TimeInterval? = nil) {
        // default parameter for tracerProvider.forceFlush() is nil, so
        // it will wait indefinitely
        // Parameter timeout: Maximum time the flush complete or abort. If nil, it will wait indefinitely
        tracerProvider.forceFlush(timeout: timeout)
    }
    
    public func shutdown() {
        tracerProvider.shutdown()
    }
}
