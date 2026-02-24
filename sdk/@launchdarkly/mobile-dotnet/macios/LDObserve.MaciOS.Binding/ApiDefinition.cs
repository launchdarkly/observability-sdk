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

        [Export("maskLabels")]
        bool MaskLabels { get; set; }

        [Export("maskImages")]
        bool MaskImages { get; set; }
    }

    [BaseType(typeof(NSObject))]
    interface SRClient
    {
        [Export("version")]
        string Version();

        [Export("startWithMobileKey:observability:replay:")]
        void Start(string mobileKey, ObjcObservabilityOptions observability, ObjcSessionReplayOptions replay);
    }

    [BaseType(typeof(NSObject))]
    interface LDObserveBridge
    {
        // + (void)recordLogWithMessage:(NSString*)message severity:(NSInteger)severity attributes:(NSDictionary*)attributes;
        [Static, Export("recordLogWithMessage:severity:attributes:")]
        void RecordLog(string message, nint severity, NSDictionary attributes);
    }
}