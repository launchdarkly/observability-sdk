import Foundation.NSDate
import UIKit.UIApplication
import ObserveAPI
import OpenTelemetryApi
import OpenTelemetrySdk
import ResourceExtension
@_exported import Instrumentation
import Shared

import Combine

public final class ObservabilityClient: @unchecked Sendable, Observe {
    private let lock = NSLock()
    private let tracerFacade: TracerFacade
    private let loggerFacade: LoggerFacade
    private let meterFacade: MeterFacade
    private var session: Session
    
    private var cachedGauges = AtomicDictionary<String, DoubleGauge>()
    private var cachedCounters = AtomicDictionary<String, DoubleCounter>()
    private var cachedLongCounters = AtomicDictionary<String, LongCounter>()
    private var cachedHistograms = AtomicDictionary<String, DoubleHistogram>()
    private var cachedUpDownCounters = AtomicDictionary<String, DoubleUpDownCounter>()

    private var cachedSpans = [String: Span]()
    private var cancellables = Set<AnyCancellable>()
    
    private var onWillEndSession: @Sendable (_ sessionId: String) -> Void {
        { [weak self] sessionId in
            self?.willEndSession(sessionId)
        }
    }
    private var onDidStartSession: @Sendable (_ sessionId: String) -> Void {
        { [weak self] sessionId in
            self?.didStartSession(sessionId)
        }
    }
    
    private var onWillTerminate: @Sendable () -> Void {
        { [weak self] in
            self?.tracerFacade.shutdown()
        }
    }
    
    deinit {
        // If observability client is used through the LDObserve wrapper, this never will be call
        // since the LDObserve wrapper is a singleton so, at some point we will need to call it manually
        self.tracerFacade.shutdown()
    }
    
    public init(configuration: Configuration = .init(), sdkKey: String = "") {
        self.tracerFacade = TracerFacade(configuration: configuration)
        self.loggerFacade = LoggerFacade(configuration: configuration)
        self.meterFacade = MeterFacade(configuration: configuration)
        self.session = Session(options: SessionOptions(timeout: configuration.sessionTimeout))
        self.registerPropagators()

        
        self.session.start(
            onWillEndSession: onWillEndSession,
            onDidStartSession: onDidStartSession
        )
        self.session.onWillTerminate(onWillTerminate)
    }
    
    private func didStartSession(_ id: String) {
        let span = spanBuilder(spanName: "app.session.started")
            .setSpanKind(spanKind: .client)
            .startSpan()
        cachedSpans[id] = span
    }
    
    private func willEndSession(_ id: String) {
        guard let span = cachedSpans[id] else { return }
        span.end()
    }
    
    private func registerPropagators() {
        OpenTelemetry.registerPropagators(
            textPropagators: [
                W3CTraceContextPropagator(),
                B3Propagator(),
                JaegerPropagator(),
            ],
            baggagePropagator: W3CBaggagePropagator()
        )
    }
    
    // MARK: - Public API
    
    public static func defaultResource() -> Resource {
        DefaultResources().get()
    }
    
     public func spanBuilder(spanName: String) -> SpanBuilder {
        tracerFacade.spanBuilder(spanName: spanName)
    }
    
    
    public func spanBuilder(spanName: String, attributes: [String: AttributeValue]) -> Span {
        let builder = tracerFacade
            .spanBuilder(spanName: spanName)
            
        attributes.forEach {
            builder.setAttribute(key: $0.key, value: $0.value)
        }
        
        return builder.startSpan()
   }
    
    
    public func recordMetric(metric: ObserveAPI.Metric) {
        
        var gauge = cachedGauges[metric.name]
        if gauge == nil {
            gauge = meterFacade.meter
                .gaugeBuilder(name: metric.name)
                .build()
            cachedGauges[metric.name] = gauge
        }
        gauge?.record(value: metric.value, attributes: metric.attributes)
    }
    
    public func recordCount(metric: ObserveAPI.Metric) {
        
        var counter = cachedCounters[metric.name]
        if counter == nil {
            counter = meterFacade.meter.counterBuilder(name: metric.name).ofDoubles().build()
            cachedCounters[metric.name] = counter
        }
        counter?.add(value: metric.value, attributes: metric.attributes)
    }
    
    public func recordIncr(metric: ObserveAPI.Metric) {
        
        var counter = cachedLongCounters[metric.name]
        if counter == nil {
            counter = meterFacade.meter.counterBuilder(name: metric.name).build()
            cachedLongCounters[metric.name] = counter
        }
        counter?.add(value: 1, attributes: metric.attributes)
    }
    
    public func recordHistogram(metric: ObserveAPI.Metric) {
        
        var histogram = cachedHistograms[metric.name]
        if histogram == nil {
            histogram = meterFacade.meter.histogramBuilder(name: metric.name).build()
            cachedHistograms[metric.name] = histogram
        }
        histogram?.record(value: metric.value, attributes: metric.attributes)
    }
    
    public func recordUpDownCounter(metric: ObserveAPI.Metric) {
        
        var upDownCounter = cachedUpDownCounters[metric.name]
        if upDownCounter == nil {
            upDownCounter = meterFacade.meter.upDownCounterBuilder(name: metric.name).ofDoubles().build()
            cachedUpDownCounters[metric.name] = upDownCounter
        }
        upDownCounter?.add(value: metric.value, attributes: metric.attributes)
    }
    
    public func recordError(error: any Error, attributes: [String : OpenTelemetryApi.AttributeValue]) {
        
        let builder = tracerFacade.tracer.spanBuilder(spanName: "highlight.error")
        
        if let parent = tracerFacade.currentSpan {
            builder.setParent(parent)
        }
        
        attributes.forEach {
            builder.setAttribute(key: $0.key, value: $0.value)
        }
        
        let span = builder.startSpan()
        span.setAttributes(attributes)
        span.recordException(ErrorSpanException(error: error), attributes: attributes)
        span.end()
    }
    
    public func recordLog(message: String, severity: Severity, attributes: [String : OpenTelemetryApi.AttributeValue]) {
        
        loggerFacade.logger.logRecordBuilder()
            .setBody(.string(message))
            .setTimestamp(.now)
            .setSeverity(severity)
            .setAttributes(attributes)
            .emit()
    }
    
    public func startSpan(name: String, attributes: [String : OpenTelemetryApi.AttributeValue]) -> any OpenTelemetryApi.Span {
        
        let builder = tracerFacade
            .spanBuilder(spanName: name)

        if let parent = tracerFacade.currentSpan {
            builder.setParent(parent)
        }
        
        attributes.forEach {
            builder.setAttribute(key: $0.key, value: $0.value)
        }

        return builder.startSpan()
    }
    
    public func flush() {
        
        tracerFacade.flush()
    }
}

struct ErrorSpanException: SpanException {
    private let error: Error
    var type: String {
        String(describing: error)
    }
    
    var message: String? {
        String(describing: error)
    }
    
    var stackTrace: [String]? {
        Thread.callStackSymbols
    }
    
    init(error: Error) {
        self.error = error
    }
}
