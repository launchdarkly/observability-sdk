using System.Collections.Generic;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

#if IOS
using LDObserveMaciOS;
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

        internal List<Hook> GetNativeHooks()
        {
            var hooks = new List<Hook>();
#if IOS
            var bridge = new ObservabilityBridge();
            var proxy = bridge.GetHookProxy();
            if (proxy != null)
            {
                hooks.Add(new NativeHookProxy(proxy));
            }
#endif
            return hooks;
        }
    }
}

