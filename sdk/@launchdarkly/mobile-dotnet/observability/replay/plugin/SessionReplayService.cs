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
    internal class SessionReplayService : INativePlugin
    {
        internal SessionReplayOptions Options { get; }
        internal ILdClient? Client { get; set; }
        internal EnvironmentMetadata? Metadata { get; set; }

        internal SessionReplayService(SessionReplayOptions options)
        {
            Options = options;
        }

        internal NativeSessionReplayHookExporter? GetNativeSessionReplayHookExporter()
        {
#if IOS
            var bridge = new LDObserveMaciOS.ObservabilityBridge();
            var proxy = bridge.GetSessionReplayHookProxy();
            if (proxy != null)
            {
                return new NativeSessionReplayHookExporter(proxy);
            }
#elif ANDROID
            var bridge = new LDObserveAndroid.ObservabilityBridge();
            var proxy = bridge.SessionReplayHookProxy;
            if (proxy != null)
            {
                return new NativeSessionReplayHookExporter(proxy);
            }
#endif
            return null;
        }
    }
}
