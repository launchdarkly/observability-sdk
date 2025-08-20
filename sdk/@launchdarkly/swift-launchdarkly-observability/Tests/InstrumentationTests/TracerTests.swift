import Testing
import Instrumentation
import OpenTelemetrySdk
import OpenTelemetryApi

struct TracerTests {
    let sut = TracerFacade(
        configuration: Configuration(
            otlpEndpoint: "http://127.0.0.1:4318",
            isDebug: true
        )
    )
    
    @Test func tracer() async throws {
        let span = sut
            .spanBuilder(spanName: "Push: details")
            .setSpanKind(spanKind: .client)
            .startSpan()
        defer { span.end() }
        let random = Int.random(in: 1..<10)
        
        try await wait(for: 12)

        #expect(random > 0)
    }
    
    @Test func currentSpan() async throws {
        let parentSpan = sut
            .spanBuilder(spanName: "Parent")
            .setSpanKind(spanKind: .client)
            .startSpan()
        
        
        try await wait(for: 1)
        
        let childSpan = sut
            .spanBuilder(spanName: "Child")
            .setParent(parentSpan)
            .startSpan()
        
        
        
        let attributes = [
            "screen" : AttributeValue.string("sign in"),
        ]
        
        childSpan.addEvent(name: "submit pressed", attributes: attributes)
        
        try await wait(for: 0.5)
        
        childSpan.addEvent(name: "activity started", attributes: attributes)
        
        try await wait(for: 0.5)
        
        childSpan.addEvent(name: "test event", timestamp: .now)
        
        try await wait(for: 1)
        
        childSpan.addEvent(name: "activity stopped")
        
        defer { parentSpan.end() }
        
        defer { childSpan.end() }
        
        try await wait(for: 3)
    }
}



