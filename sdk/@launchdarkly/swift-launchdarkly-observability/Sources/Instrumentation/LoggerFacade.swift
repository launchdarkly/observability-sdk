@preconcurrency import OpenTelemetryApi
import OpenTelemetrySdk
import StdoutExporter
import OpenTelemetryProtocolExporterHttp

public struct LoggerFacade {
    private let configuration: Configuration
    private let LoggerProvider: LoggerProvider
    public let logger: Logger
    
    public init(configuration: Configuration) {
        func buildExporter(using configuration: Configuration) -> LogRecordExporter {
            var logRecordExporters = [any LogRecordExporter]()
            
            if let httpExporter = buildHttpExporter(using: configuration) {
                logRecordExporters.append(httpExporter)
            }
            
            if configuration.isDebug {
                logRecordExporters.append(
                    StdoutLogExporter(isDebug: configuration.isDebug)
                )
            }
            
            return MultiLogRecordExporter(logRecordExporters: logRecordExporters)
        }
        
        func buildHttpExporter(using configuration: Configuration) -> (OtlpHttpExporterBase & LogRecordExporter)? {
            guard let baseUrl = URL(string: configuration.otlpEndpoint) else {
                print("Trace exporter URL is invalid")
                return nil
            }
            let url = baseUrl.appending(path: HttpExporterPath.logs)
            return OtlpHttpLogExporter(
                endpoint: url,
                envVarHeaders: configuration.customHeaders
            )
        }
        
        func buildLoggerProvider(using resource: Resource) -> LoggerProvider {
            LoggerProviderBuilder()
                .with(
                    processors: [
                        BatchLogRecordProcessor(
                            logRecordExporter: buildExporter(using: configuration)
                        )
                    ]
                )
                .with(resource: Resource(attributes: configuration.resourceAttributes))
                .build()
        }
        self.configuration = configuration
        let loggerProvider = buildLoggerProvider(using: Resource(attributes: configuration.resourceAttributes))
        OpenTelemetry.registerLoggerProvider(
            loggerProvider: loggerProvider
        )
        self.LoggerProvider = loggerProvider
        self.logger = OpenTelemetry.instance.loggerProvider.get(
            instrumentationScopeName: configuration.serviceName
        )
    }
    
    // MARK: - Public API
    
    public func eventProvider() -> LogRecordBuilder {
        logger.logRecordBuilder()
    }
}
