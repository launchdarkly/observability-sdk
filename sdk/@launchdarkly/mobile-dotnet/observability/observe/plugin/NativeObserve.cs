using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Integrations.Plugins;

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
            var proxy = LDObserveMaciOS.LDObserveBridge.GetObservabilityHookProxy();
            if (proxy != null)
            {
                return new NativeObservabilityHookExporter(proxy);
            }
#elif ANDROID
            var proxy = LDObserveAndroid.LDObserveBridgeAdapter.ObservabilityHookProxy;
            if (proxy != null)
            {
                return new NativeObservabilityHookExporter(proxy);
            }
#endif
            return null;
        }
    }
}

