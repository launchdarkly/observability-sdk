using System.Collections.Generic;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Client.Plugins;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    /// <summary>
    /// Single LaunchDarkly plugin that wires up both observability and session
    /// replay. When registered on an <see cref="LaunchDarkly.Sdk.Client.ILdClient"/>
    /// it boots the native stack once and installs the relevant hooks.
    /// </summary>
    public class ObservabilityPlugin : Plugin
    {
        internal ObservabilityService ObservabilityService { get; }
        internal SessionReplayService? SessionReplayService { get; }

        public ObservabilityPlugin(ObservabilityOptions observability, SessionReplayOptions? replay = null)
            : base("LaunchDarkly.Observability")
        {
            ObservabilityService = new ObservabilityService(observability);
            SessionReplayService = replay != null ? new SessionReplayService(replay) : null;
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            ObservabilityService.Client = client;
            ObservabilityService.Metadata = metadata;

            if (SessionReplayService != null)
            {
                SessionReplayService.Client = client;
                SessionReplayService.Metadata = metadata;
            }

            var replayOptions = SessionReplayService?.Options ?? new SessionReplayOptions(isEnabled: false);
            LDNative.Start(metadata.Credential, ObservabilityService.Options, replayOptions);

            if (ObservabilityService.Options.IsEnabled)
                LDObserve.Initialize(ObservabilityService);
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            ObservabilityService.Metadata = metadata;
            var hooks = new List<Hook> { new ObservabilityHook(ObservabilityService) };

            if (SessionReplayService != null)
            {
                SessionReplayService.Metadata = metadata;
                hooks.Add(new SessionReplayHook(SessionReplayService));
            }

            return hooks;
        }
    }
}
