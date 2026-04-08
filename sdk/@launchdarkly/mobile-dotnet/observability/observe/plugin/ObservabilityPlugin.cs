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
        internal ObservabilityService ObservabilityService { get; private set; }

        public ObservabilityPlugin(ObservabilityOptions options) : base("LaunchDarkly.Observability")
        {
#if ANDROID
            if (options.Instrumentation.NetworkRequests)
                AppContext.SetSwitch("System.Net.Http.EnableActivityPropagation", true);
#endif
            ObservabilityService = new ObservabilityService(options);
            PluginOrchestrator.Instance.AddObservabilityService(ObservabilityService);
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            ObservabilityService.Client = client;
            ObservabilityService.Metadata = metadata;
            PluginOrchestrator.Instance.Register();
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            ObservabilityService.Metadata = metadata;
            return new List<Hook> { new ObservabilityHook(ObservabilityService) };
        }
    }
}
