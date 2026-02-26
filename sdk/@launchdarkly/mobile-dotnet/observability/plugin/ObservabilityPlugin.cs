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
        }

        internal ObservabilityPlugin() : base("LaunchDarkly.Observability")
        {
            _options = null;
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            if (_options == null) return;

            // TODO: initialize native observability with _options and metadata.Credential
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            // TODO: return tracing hooks once a client-side TracingHook is available
            return new List<Hook>();
        }

        public sealed class ObservabilityPluginBuilder
        {
            private string _serviceName = ObservabilityOptions.DefaultServiceName;
            private string _serviceVersion = ObservabilityOptions.DefaultServiceVersion;
            private string _otlpEndpoint = ObservabilityOptions.DefaultOtlpEndpoint;
            private string _backendUrl = ObservabilityOptions.DefaultBackendUrl;

            internal ObservabilityPluginBuilder()
            {
            }

            internal ObservabilityPluginBuilder(ObservabilityOptions options)
            {
                if (options == null) throw new ArgumentNullException(nameof(options));
                _serviceName = options.ServiceName;
                _serviceVersion = options.ServiceVersion;
                _otlpEndpoint = options.OtlpEndpoint;
                _backendUrl = options.BackendUrl;
            }

            public ObservabilityPluginBuilder WithServiceName(string serviceName)
            {
                _serviceName = serviceName ?? throw new ArgumentNullException(nameof(serviceName));
                return this;
            }

            public ObservabilityPluginBuilder WithServiceVersion(string serviceVersion)
            {
                _serviceVersion = serviceVersion ?? throw new ArgumentNullException(nameof(serviceVersion));
                return this;
            }

            public ObservabilityPluginBuilder WithOtlpEndpoint(string otlpEndpoint)
            {
                _otlpEndpoint = otlpEndpoint ?? throw new ArgumentNullException(nameof(otlpEndpoint));
                return this;
            }

            public ObservabilityPluginBuilder WithBackendUrl(string backendUrl)
            {
                _backendUrl = backendUrl ?? throw new ArgumentNullException(nameof(backendUrl));
                return this;
            }

            internal ObservabilityOptions BuildOptions()
            {
                return new ObservabilityOptions(
                    serviceName: _serviceName,
                    serviceVersion: _serviceVersion,
                    otlpEndpoint: _otlpEndpoint,
                    backendUrl: _backendUrl
                );
            }

            public ObservabilityPlugin Build()
            {
                return new ObservabilityPlugin(BuildOptions());
            }
        }
    }
}
