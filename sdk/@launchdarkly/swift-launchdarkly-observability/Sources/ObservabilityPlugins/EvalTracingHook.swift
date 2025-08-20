import Foundation
import LaunchDarkly
import OpenTelemetryApi
import OpenTelemetrySdk
import Instrumentation
import Shared

public final class EvalTracingHook: @unchecked Sendable, Hook {
    private let lock: NSLock = NSLock()
    private let withSpans: Bool
    private let withValue: Bool
    
    public init(withSpans: Bool, withValue: Bool) {
        self.withSpans = withSpans
        self.withValue = withValue
    }
    
    public func beforeEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: EvaluationSeriesData
    ) -> EvaluationSeriesData {
        lock.lock()
        defer { lock.unlock() }
        guard withSpans else { return seriesData }
        
        
        let tracer = TracerFacade(configuration: .init(serviceName: Self.INSTRUMENTATION_NAME))
        guard let activeSpan = tracer.currentSpan else { return seriesData }
        let builder = tracer
            .spanBuilder(spanName: seriesContext.methodName)
            .setParent(activeSpan)
            
        var resourceAttributes = [String: AttributeValue]()
        resourceAttributes[Self.SEMCONV_FEATURE_FLAG_KEY] = .string(seriesContext.flagKey)
        resourceAttributes[Self.SEMCONV_FEATURE_FLAG_PROVIDER_NAME] = .string(Self.PROVIDER_NAME)
        resourceAttributes.forEach {
            builder.setAttribute(key: $0.key, value: $0.value)
        }
        let span = builder.startSpan()
        var mutableSeriesData = seriesData
        mutableSeriesData[Self.DATA_KEY_SPAN] = span
        
        return mutableSeriesData
    }
    
    public func afterEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: EvaluationSeriesData,
        evaluationDetail: LDEvaluationDetail<LDValue>
    ) -> EvaluationSeriesData {
        let value = seriesData[Self.DATA_KEY_SPAN]
        if let span = value as? Span {
            span.end()
        }
     
        var resourceAttributes = [String: AttributeValue]()
        resourceAttributes[Self.SEMCONV_FEATURE_FLAG_KEY] = .string(seriesContext.flagKey)
        resourceAttributes[Self.SEMCONV_FEATURE_FLAG_PROVIDER_NAME] = .string(Self.PROVIDER_NAME)
        
        if let lDValue = evaluationDetail.reason?[Self.CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT] {
            if case let .bool(inExperiment) = lDValue {
                resourceAttributes[Self.CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT] = .bool(inExperiment)
            }
        }
        resourceAttributes[Self.SEMCONV_FEATURE_FLAG_CONTEXT_ID] = .string(seriesContext.context.fullyQualifiedKey())
        if withValue {
            if let stringified = JSON.stringify(evaluationDetail.value) {
                resourceAttributes[Self.SEMCONV_FEATURE_FLAG_RESULT_VALUE] = .string(stringified) // .string is from Otel AttributeValue
            }
        }
        
        if let index = evaluationDetail.variationIndex {
            resourceAttributes[Self.CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX] = .double(Double(index))
        }
        
        let tracer = TracerFacade(configuration: .init(serviceName: Self.INSTRUMENTATION_NAME))
        guard let current = tracer.currentSpan else { return seriesData }
        
        current.addEvent(name: Self.EVENT_NAME, attributes: resourceAttributes)
        
        return seriesData
    }
}

extension EvalTracingHook {
    static let PROVIDER_NAME: String = "LaunchDarkly"
    static let HOOK_NAME: String = "LaunchDarkly Evaluation Tracing Hook"
    static let INSTRUMENTATION_NAME: String = "com.launchdarkly.observability"
    static let DATA_KEY_SPAN: String = "variationSpan"
    static let EVENT_NAME: String = "feature_flag"
    static let SEMCONV_FEATURE_FLAG_CONTEXT_ID: String = "feature_flag.context.id"
    static let SEMCONV_FEATURE_FLAG_PROVIDER_NAME: String = "feature_flag.provider.name"
    static let SEMCONV_FEATURE_FLAG_KEY: String = "feature_flag.key"
    static let SEMCONV_FEATURE_FLAG_RESULT_VALUE: String = "feature_flag.result.value"
    static let CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX: String = "feature_flag.result.variationIndex"
    static let CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT: String = "feature_flag.result.reason.inExperiment"
    static let FEATURE_FLAG_SPAN_NAME = "evaluation"
}
