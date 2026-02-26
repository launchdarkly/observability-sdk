using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    internal class NativeSessionReplay : INativePlugin
    {
        internal SessionReplayOptions Options { get; }
        internal ILdClient? Client { get; set; }
        internal EnvironmentMetadata? Metadata { get; set; }

        internal NativeSessionReplay(SessionReplayOptions options)
        {
            Options = options;
        }

        public void Initialize()
        {
            // TODO: initialize native session replay with Options, Client, and Metadata
        }
    }
}
