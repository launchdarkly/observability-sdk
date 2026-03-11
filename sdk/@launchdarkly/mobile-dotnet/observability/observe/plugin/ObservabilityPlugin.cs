using System;
using System.Collections.Generic;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Client.Plugins;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    public class ObservabilityPlugin : Plugin
    {
        private readonly ObservabilityOptions? _options;

        public static ObservabilityPlugin ForExistingServices() => new ObservabilityPlugin();

        public ObservabilityPlugin(ObservabilityOptions options) : base("LaunchDarkly.Observability")
        {
            _options = options ?? throw new ArgumentNullException(nameof(options));
            NativePluginConnector.Instance.CreateObserve(options);
        }

        internal ObservabilityPlugin() : base("LaunchDarkly.Observability")
        {
            _options = null;
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            if (_options == null) return;
            NativePluginConnector.Instance.RegisterObserve(client, metadata);
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            return NativePluginConnector.Instance.GetHooksObserve(metadata);
        }
    }
}
