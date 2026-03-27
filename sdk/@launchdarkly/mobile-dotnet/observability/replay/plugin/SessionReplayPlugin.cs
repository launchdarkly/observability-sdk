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
        internal NativeSessionReplay SessionReplay { get; private set; }

        public SessionReplayPlugin(SessionReplayOptions options) : base("LaunchDarkly.SessionReplay")
        {
            SessionReplay = new NativeSessionReplay(options);

            PluginOrchestrator.Instance.AddSessionReplay(SessionReplay);
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            SessionReplay.Client = client;
            SessionReplay.Metadata = metadata;
            PluginOrchestrator.Instance.Register();
        }
        
        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            SessionReplay.Metadata = metadata;
            return new List<Hook> { new SessionReplayHook(SessionReplay) };
        }
    }
}
