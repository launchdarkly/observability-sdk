using System;
using System.Collections.Generic;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Client.Plugins;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    public class SessionReplayPlugin : Plugin
    {
        private readonly SessionReplayOptions? _options;

        public static SessionReplayPlugin ForExistingServices() => new SessionReplayPlugin();

        public SessionReplayPlugin(SessionReplayOptions options) : base("LaunchDarkly.SessionReplay")
        {
            _options = options ?? throw new ArgumentNullException(nameof(options));
            NativePluginConnector.Instance.CreateSessionReplay(options);
        }

        internal SessionReplayPlugin() : base("LaunchDarkly.SessionReplay")
        {
            _options = null;
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            if (_options == null) return;
            NativePluginConnector.Instance.RegisterSessionReplay(client, metadata);
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            return NativePluginConnector.Instance.GetHooksSessionReplay(metadata);
        }
    }
}
