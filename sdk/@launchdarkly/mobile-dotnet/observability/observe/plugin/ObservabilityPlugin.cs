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

        public static ObservabilityPluginBuilder Builder() => new ObservabilityPluginBuilder();

        public static ObservabilityPluginBuilder Builder(ObservabilityOptions options) =>
            new ObservabilityPluginBuilder(options);

        internal ObservabilityPlugin(ObservabilityOptions options) : base("LaunchDarkly.Observability")
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

        public sealed class ObservabilityPluginBuilder
        {
            private readonly ObservabilityOptions _options;

            internal ObservabilityPluginBuilder()
            {
                _options = new ObservabilityOptions();
            }

            internal ObservabilityPluginBuilder(ObservabilityOptions options)
            {
                _options = options ?? throw new ArgumentNullException(nameof(options));
            }

            public ObservabilityPluginBuilder WithIsEnabled(bool isEnabled)
            {
                _options.IsEnabled = isEnabled;
                return this;
            }

            public ObservabilityPluginBuilder WithServiceName(string serviceName)
            {
                _options.ServiceName = serviceName ?? throw new ArgumentNullException(nameof(serviceName));
                return this;
            }

            public ObservabilityPluginBuilder WithServiceVersion(string serviceVersion)
            {
                _options.ServiceVersion = serviceVersion ?? throw new ArgumentNullException(nameof(serviceVersion));
                return this;
            }

            public ObservabilityPluginBuilder WithOtlpEndpoint(string otlpEndpoint)
            {
                _options.OtlpEndpoint = otlpEndpoint ?? throw new ArgumentNullException(nameof(otlpEndpoint));
                return this;
            }

            public ObservabilityPluginBuilder WithBackendUrl(string backendUrl)
            {
                _options.BackendUrl = backendUrl ?? throw new ArgumentNullException(nameof(backendUrl));
                return this;
            }

            internal ObservabilityOptions BuildOptions() => _options;

            public ObservabilityPlugin Build()
            {
                return new ObservabilityPlugin(BuildOptions());
            }
        }
    }
}
