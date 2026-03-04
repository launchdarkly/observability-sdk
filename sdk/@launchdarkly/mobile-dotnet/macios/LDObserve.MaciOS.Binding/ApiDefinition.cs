using Foundation;
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

        [Export("getHookProxy")]
        [NullAllowed]
        ObservabilityHookProxy GetHookProxy();
    }

    [BaseType(typeof(NSObject))]
    interface LDObserveBridge
    {
        [Static, Export("recordLogWithMessage:severity:attributes:")]
        void RecordLog(string message, nint severity, NSDictionary attributes);
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
}