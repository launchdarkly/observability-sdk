import OpenTelemetryApi
import Instrumentation
import Client
import ObserveAPI

private final class NoOpClient: Observe {
    func recordMetric(metric: ObserveAPI.Metric) {}
    func recordCount(metric: ObserveAPI.Metric) {}
    func recordIncr(metric: ObserveAPI.Metric) {}
    func recordHistogram(metric: ObserveAPI.Metric) {}
    func recordUpDownCounter(metric: ObserveAPI.Metric) {}
    func recordError(error: any Error, attributes: [String : OpenTelemetryApi.AttributeValue]) {}
    func recordLog(message: String, severity: Severity, attributes: [String : OpenTelemetryApi.AttributeValue]) {}
    func startSpan(name: String, attributes: [String : OpenTelemetryApi.AttributeValue]) -> any OpenTelemetryApi.Span {
        TracerFacade(configuration: .init()).spanBuilder(spanName: "").startSpan()
    }
    func flush() {}
}

public final class LDObserve: @unchecked Sendable, Observe {
    private let lock = NSLock()
    private var client: Observe = NoOpClient()
    
    public static let shared = LDObserve()
    
    private init() {}
    
    // To prevent race conditions when set client
    public func set(client: Observe) {
        lock.lock()
        defer { lock.unlock() }
        self.client = client
    }
    
    public func recordMetric(metric: ObserveAPI.Metric) {
        lock.lock()
        defer { lock.unlock() }
        client.recordIncr(metric: metric)
    }
    
    public func recordCount(metric: ObserveAPI.Metric) {
        lock.lock()
        defer { lock.unlock() }
        client.recordCount(metric: metric)
    }
    
    public func recordIncr(metric: ObserveAPI.Metric) {
        lock.lock()
        defer { lock.unlock() }
        client.recordIncr(metric: metric)
    }
    
    public func recordHistogram(metric: ObserveAPI.Metric) {
        lock.lock()
        defer { lock.unlock() }
        client.recordHistogram(metric: metric)
    }
    
    public func recordUpDownCounter(metric: ObserveAPI.Metric) {
        lock.lock()
        defer { lock.unlock() }
        client.recordUpDownCounter(metric: metric)
    }
    
    public func recordError(error: any Error, attributes: [String : OpenTelemetryApi.AttributeValue]) {
        lock.lock()
        defer { lock.unlock() }
        client.recordError(error: error, attributes: attributes)
    }
    
    public func recordLog(message: String, severity: Severity, attributes: [String : OpenTelemetryApi.AttributeValue]) {
        lock.lock()
        defer { lock.unlock() }
        client.recordLog(message: message, severity: severity, attributes: attributes)
    }
    
    public func startSpan(name: String, attributes: [String : OpenTelemetryApi.AttributeValue]) -> any OpenTelemetryApi.Span {
        lock.lock()
        defer { lock.unlock() }
        return client.startSpan(name: name, attributes: attributes)
    }
    
    public func flush() {
        lock.lock()
        defer { lock.unlock() }
        client.flush()
    }
}
