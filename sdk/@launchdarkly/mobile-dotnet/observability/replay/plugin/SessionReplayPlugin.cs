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
        internal SessionReplayService SessionReplayService { get; private set; }

        public SessionReplayPlugin(SessionReplayOptions options) : base("LaunchDarkly.SessionReplay")
        {
            SessionReplayService = new SessionReplayService(options);
            PluginOrchestrator.Instance.AddSessionReplayService(SessionReplayService);
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            SessionReplayService.Client = client;
            SessionReplayService.Metadata = metadata;
            PluginOrchestrator.Instance.Register();
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            SessionReplayService.Metadata = metadata;
            return new List<Hook> { new SessionReplayHook(SessionReplayService) };
        }
    }
}
