using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.Sdk.Server.Interfaces;
using LaunchDarkly.Sdk.Server.Plugins;

public class ObservabilityPlugin : Plugin
    {
        public ObservabilityPlugin(string name = "observability-plugin")
            :base(name) { }

        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            // No-op for testing
        }
    }