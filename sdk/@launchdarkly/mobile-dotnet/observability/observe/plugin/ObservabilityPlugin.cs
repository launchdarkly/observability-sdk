using System;
using System.Collections.Generic;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Client.Plugins;
using LaunchDarkly.Sdk.Integrations.Plugins;

namespace LaunchDarkly.Observability
{
    public class ObservabilityPlugin : Plugin
    {
        internal NativeObserve Observe { get; private set; }

        public ObservabilityPlugin(ObservabilityOptions options) : base("LaunchDarkly.Observability")
        {
            Observe = new NativeObserve(options);
            PluginOrchestrator.Instance.AddObserve(Observe);
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            Observe.Client = client;
            Observe.Metadata = metadata;
            PluginOrchestrator.Instance.Register();
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            Observe.Metadata = metadata;
            return new List<Hook> { new ObservabilityHook(Observe) };
        }
    }
}
