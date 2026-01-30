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

        [Export("maskImages")]
        bool MaskImages { get; set; }
    }

    [BaseType(typeof(NSObject))]
    interface SRClient
    {
        [Static]
        [Export("startExample")]
        string startExample();

        [Static]
        [Export("startWithMobileKey:observability:replay:")]
        void Start(string mobileKey, ObjcObservabilityOptions observability, ObjcSessionReplayOptions replay);

        [Static]
        [Export("startWithMobileKey:observabilityDictionary:replayDictionary:")]
        void Start(string mobileKey, NSDictionary observability, NSDictionary replay);
    }

    [BaseType(typeof(NSObject))]
    interface LDObserveBridge
    {
        // + (void)recordLogWithMessage:(NSString*)message severity:(NSInteger)severity attributes:(NSDictionary*)attributes;
        [Static, Export("recordLogWithMessage:severity:attributes:")]
        void RecordLog(string message, nint severity, NSDictionary attributes);
    }
}