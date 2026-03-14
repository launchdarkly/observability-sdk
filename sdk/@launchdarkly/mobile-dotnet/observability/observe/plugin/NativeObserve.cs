using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

#if IOS
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.Observability
{
    internal class NativeObserve : INativePlugin
    {
        internal ObservabilityOptions Options { get; }
        internal ILdClient? Client { get; set; }
        internal EnvironmentMetadata? Metadata { get; set; }

        internal NativeObserve(ObservabilityOptions options)
        {
            Options = options;
        }

        public void Initialize()
        {
            // TODO: initialize native observability with Options, Client, and Metadata
        }

        internal NativeObservabilityHookExporter? GetNativeHookExporter()
        {
#if IOS
            var bridge = new LDObserveMaciOS.ObservabilityBridge();
            var proxy = bridge.GetObservabilityHookProxy();
            if (proxy != null)
            {
                return new NativeObservabilityHookExporter(proxy);
            }
#elif ANDROID
            var bridge = new LDObserveAndroid.ObservabilityBridge();
            var proxy = bridge.ObservabilityHookProxy;
            if (proxy != null)
            {
                return new NativeObservabilityHookExporter(proxy);
            }
#endif
            return null;
        }
    }
}

