using System;
using Foundation;
using ObjCRuntime;
using UIKit;

namespace LDObserveMaciOS
{
    [BaseType(typeof(NSObject))]
    interface LDClient
    {
        [Static]
        [Export("startExample")]
        string startExample();
    }

    [BaseType(typeof(NSObject))]
    interface ObjcObservabilityOptions
    {
        [Export("serviceName")]
        string ServiceName { get; set; }

        [Export("serviceVersion")]
        string ServiceVersion { get; set; }

        [Export("otlpEndpoint")]
        string OtlpEndpoint { get; set; }

        [Export("backendUrl")]
        string BackendUrl { get; set; }

        [NullAllowed, Export("attributes")]
        NSDictionary Attributes { get; set; }
    }

    [BaseType(typeof(NSObject))]
    interface ObjcSessionReplayOptions
    {
        [Export("isEnabled")]
        bool IsEnabled { get; set; }

        [Export("maskTextInputs")]
        bool MaskTextInputs { get; set; }

        [Export("maskWebViews")]
        bool MaskWebViews { get; set; }

        [Export("maskLabels")]
        bool MaskLabels { get; set; }

        [Export("maskImages")]
        bool MaskImages { get; set; }
    }

    [BaseType(typeof(NSObject))]
    interface ObservabilityBridge
    {
        [Export("version")]
        string Version();

        [Export("startWithMobileKey:observability:replay:")]
        void Start(string mobileKey, ObjcObservabilityOptions observability, ObjcSessionReplayOptions replay);

        [Export("getSessionReplayHookProxy")]
        [NullAllowed]
        SessionReplayHookProxy GetSessionReplayHookProxy();
    }

    [BaseType(typeof(NSObject))]
    interface LDObserveBridge
    {
        [Static, Export("recordLogWithMessage:severity:attributes:")]
        void RecordLog(string message, nint severity, NSDictionary attributes);

        [Static, Export("recordErrorWithMessage:cause:")]
        void RecordError(string message, [NullAllowed] string cause);

        [Static, Export("recordMetricWithName:value:")]
        void RecordMetric(string name, double value);

        [Static, Export("recordCountWithName:value:")]
        void RecordCount(string name, double value);

        [Static, Export("recordIncrWithName:value:")]
        void RecordIncr(string name, double value);

        [Static, Export("recordHistogramWithName:value:")]
        void RecordHistogram(string name, double value);

        [Static, Export("recordUpDownCounterWithName:value:")]
        void RecordUpDownCounter(string name, double value);

        [Static, Export("getObservabilityHookProxy")]
        [return: NullAllowed]
        ObservabilityHookProxy GetObservabilityHookProxy();

        [Static, Export("getObjcTracer")]
        [return: NullAllowed]
        ObjcTracer GetObjcTracer();
    }

    [BaseType(typeof(NSObject))]
    interface ObjcTracer
    {
        [Export("spanBuilderWithName:startTime:traceId:parentSpanId:")]
        ObjcSpanBuilder SpanBuilder(string name, double startTime, string traceId, string parentSpanId);
    }

    [BaseType(typeof(NSObject))]
    interface ObjcSpanBuilder
    {
        [Export("traceId")]
        string TraceId { get; }

        [Export("spanId")]
        string SpanId { get; }

        [Export("spanKind")]
        nint SpanKind { get; }

        [Export("setAttributeWithKey:value:")]
        void SetAttribute(string key, NSObject value);

        [Export("setAttributes:")]
        void SetAttributes(NSDictionary attributes);

        [Export("addEventWithName:")]
        void AddEvent(string name);

        [Export("addEventWithName:attributes:")]
        void AddEvent(string name, NSDictionary attributes);

        [Export("recordExceptionWithMessage:type:")]
        void RecordException(string message, string type);

        [Export("recordExceptionWithMessage:type:attributes:")]
        void RecordException(string message, string type, NSDictionary attributes);

        [Export("setStatusCode:")]
        void SetStatus(nint code);

        [Export("endWithTime:")]
        void End(double time);
    }

    [BaseType(typeof(NSObject))]
    interface ObservabilityHookProxy
    {
        [Export("beforeEvaluationWithId:flagKey:contextKey:")]
        void BeforeEvaluation(string evaluationId, string flagKey, string contextKey);

        [Export("afterEvaluationWithId:flagKey:contextKey:value:variationIndex:reason:")]
        void AfterEvaluation(string evaluationId, string flagKey, string contextKey,
            NSObject value, nint variationIndex, [NullAllowed] NSDictionary reason);

        [Export("afterIdentifyWithContextKeys:canonicalKey:completed:")]
        void AfterIdentify(NSDictionary contextKeys, string canonicalKey, bool completed);
    }

    [BaseType(typeof(NSObject))]
    interface SessionReplayHookProxy
    {
        [Export("afterIdentifyWithContextKeys:canonicalKey:completed:")]
        void AfterIdentify(NSDictionary contextKeys, string canonicalKey, bool completed);
    }
}