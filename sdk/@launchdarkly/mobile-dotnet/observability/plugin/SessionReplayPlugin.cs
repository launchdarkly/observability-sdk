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

        public static SessionReplayPluginBuilder Builder() => new SessionReplayPluginBuilder();

        public static SessionReplayPluginBuilder Builder(SessionReplayOptions options) =>
            new SessionReplayPluginBuilder(options);

        internal SessionReplayPlugin(SessionReplayOptions options) : base("LaunchDarkly.SessionReplay")
        {
            _options = options ?? throw new ArgumentNullException(nameof(options));
        }

        internal SessionReplayPlugin() : base("LaunchDarkly.SessionReplay")
        {
            _options = null;
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            if (_options == null) return;

            // TODO: initialize native session replay with _options and metadata.Credential
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            return new List<Hook>();
        }

        public sealed class SessionReplayPluginBuilder
        {
            private bool _isEnabled = true;
            private string _serviceName = "sessionreplay-dotnet";
            private SessionReplayOptions.PrivacyOptions _privacy = new SessionReplayOptions.PrivacyOptions();

            internal SessionReplayPluginBuilder()
            {
            }

            internal SessionReplayPluginBuilder(SessionReplayOptions options)
            {
                if (options == null) throw new ArgumentNullException(nameof(options));
                _isEnabled = options.IsEnabled;
                _serviceName = options.ServiceName;
                _privacy = options.Privacy;
            }

            public SessionReplayPluginBuilder WithIsEnabled(bool isEnabled)
            {
                _isEnabled = isEnabled;
                return this;
            }

            public SessionReplayPluginBuilder WithServiceName(string serviceName)
            {
                _serviceName = serviceName ?? throw new ArgumentNullException(nameof(serviceName));
                return this;
            }

            public SessionReplayPluginBuilder WithPrivacy(SessionReplayOptions.PrivacyOptions privacy)
            {
                _privacy = privacy ?? throw new ArgumentNullException(nameof(privacy));
                return this;
            }

            internal SessionReplayOptions BuildOptions()
            {
                return new SessionReplayOptions(
                    isEnabled: _isEnabled,
                    serviceName: _serviceName,
                    privacy: _privacy
                );
            }

            public SessionReplayPlugin Build()
            {
                return new SessionReplayPlugin(BuildOptions());
            }
        }
    }
}
