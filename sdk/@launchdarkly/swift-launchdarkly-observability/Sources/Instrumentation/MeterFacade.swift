@preconcurrency import OpenTelemetryApi
import OpenTelemetrySdk
import StdoutExporter
import OpenTelemetryProtocolExporterHttp

public struct MeterFacade {
    private let configuration: Configuration
    private let meterProvider: MeterProvider
    public let meter: any Meter
    
    public init(configuration: Configuration) {
        func buildExporter(using configuration: Configuration) -> MetricExporter {
            var metricExporters = [any MetricExporter]()
            
            if let httpExporter = buildHttpExporter(using: configuration) {
                metricExporters.append(httpExporter)
            }
            
            if configuration.isDebug {
                metricExporters.append(
                    StdoutMetricExporter(isDebug: configuration.isDebug)
                )
            }
            // TODO: figure out how to use a multi exporter like in tracer and logger, for now, using the http exporter
            return metricExporters[0]
        }
        
        func buildHttpExporter(using configuration: Configuration) -> (MetricExporter)? {
            guard let baseUrl = URL(string: configuration.otlpEndpoint) else {
                print("Trace exporter URL is invalid")
                return nil
            }
            let url = baseUrl.appending(path: HttpExporterPath.metrics)
            return OtlpHttpMetricExporter(
                endpoint: url,
                envVarHeaders: configuration.customHeaders
            )
        }
        
        func buildReader(using exporter: MetricExporter) -> MetricReader {
            PeriodicMetricReaderBuilder(exporter: exporter)
                .setInterval(timeInterval: 10.0)
                .build()
        }
        
        func buildMeterProvider(using configuration: Configuration) -> any MeterProvider {
            MeterProviderSdk.builder()
                .registerView(
                    selector: InstrumentSelector.builder().setInstrument(name: configuration.serviceName).build(),
                    view: View.builder().build()
                )
                .registerMetricReader(
                    reader: buildReader(
                        using: buildExporter(using: configuration)
                    )
                )
                .build()
        }
        
        self.configuration = configuration
        let meterProvider = buildMeterProvider(using: configuration)
        OpenTelemetry.registerMeterProvider(
            meterProvider: meterProvider
        )
        self.meterProvider = meterProvider
        self.meter = OpenTelemetry.instance.meterProvider.get(
            name: configuration.serviceName
        )
    }
}
