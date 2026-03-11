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

        public sealed class SessionReplayPluginBuilder
        {
            private readonly SessionReplayOptions _options;

            internal SessionReplayPluginBuilder()
            {
                _options = new SessionReplayOptions();
            }

            internal SessionReplayPluginBuilder(SessionReplayOptions options)
            {
                _options = options ?? throw new ArgumentNullException(nameof(options));
            }

            public SessionReplayPluginBuilder WithIsEnabled(bool isEnabled)
            {
                _options.IsEnabled = isEnabled;
                return this;
            }

            public SessionReplayPluginBuilder WithServiceName(string serviceName)
            {
                _options.ServiceName = serviceName ?? throw new ArgumentNullException(nameof(serviceName));
                return this;
            }

            public SessionReplayPluginBuilder WithPrivacy(SessionReplayOptions.PrivacyOptions privacy)
            {
                _options.Privacy = privacy ?? throw new ArgumentNullException(nameof(privacy));
                return this;
            }

            internal SessionReplayOptions BuildOptions() => _options;

            public SessionReplayPlugin Build()
            {
                return new SessionReplayPlugin(BuildOptions());
            }
        }
    }
}
