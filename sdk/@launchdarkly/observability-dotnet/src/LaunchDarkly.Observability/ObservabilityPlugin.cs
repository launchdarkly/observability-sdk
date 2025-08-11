using System;
using System.Collections.Generic;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.Sdk.Server.Hooks;
using LaunchDarkly.Sdk.Server.Interfaces;
using LaunchDarkly.Sdk.Server.Plugins;
using LaunchDarkly.Sdk.Server.Telemetry;
using Microsoft.Extensions.DependencyInjection;

namespace LaunchDarkly.Observability
{
    public class ObservabilityPlugin : Plugin
    {
        private readonly Action<ObservabilityConfig.Builder> _configure;
        private readonly IServiceCollection _services;

        public static ObservabilityPlugin WithServices(IServiceCollection services,
            Action<ObservabilityConfig.Builder> configure) => new ObservabilityPlugin(services, configure);
        
        public static ObservabilityPlugin () => new ObservabilityPlugin()
    
        internal ObservabilityPlugin(IServiceCollection services, Action<ObservabilityConfig.Builder> configure) : base("LaunchDarkly.Observability")
        {
            _configure = configure;
            _services = services;
        }

        internal ObservabilityPlugin() : base("LaunchDarkly.Observability")
        {
            _services = null;
            _configure = null;
        }

        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            if (_services != null)
            {
                _services.AddLaunchDarklyObservability(metadata.Credential, _configure);
            }
        }

        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            return new List<Hook>
            {
                TracingHook.Builder().IncludeVariant().Build()
            };
        }
    }
}