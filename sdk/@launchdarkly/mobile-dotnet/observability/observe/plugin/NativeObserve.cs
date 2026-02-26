using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

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
    }
}
