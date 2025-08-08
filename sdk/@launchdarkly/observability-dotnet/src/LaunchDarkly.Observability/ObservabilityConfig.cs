namespace LaunchDarkly.Observability
{
    public struct ObservabilityConfig
    {
        /// <summary>
        /// The configured OTLP endpoint.
        /// </summary>
        public string OtlpEndpoint { get; }
        
        /// <summary>
        /// The configured back-end URL.
        /// <para>
        /// This is used for non-telemetry operations such as accessing the sampling configuration.
        /// </para>
        /// </summary>
        public string BackendUrl { get; }
        /// <summary>
        /// The name of the service.
        /// <para>
        /// The service name is used for adding resource attributes. If a service name is not defined, then the
        /// service version will also not be included in the resource attributes.
        /// </para>
        /// </summary>
        public string ServiceName { get; }
        /// <summary>
        /// The version of the service.
        /// </summary>
        public string ServiceVersion { get; }
        /// <summary>
        /// The environment for the service.
        /// </summary>
        public string Environment { get; }
        /// <summary>
        /// The LaunchDarkly SDK key.
        /// </summary>
        public string SdkKey { get; }

        private ObservabilityConfig(
            string otlpEndpoint,
            string backendUrl,
            string serviceName,
            string environment,
            string serviceVersion,
            string sdkKey)
        {
            OtlpEndpoint = otlpEndpoint;
            BackendUrl = backendUrl;
            ServiceName = serviceName;
            Environment = environment;
            ServiceVersion = serviceVersion;
            SdkKey = sdkKey;
        }

        /// <summary>
        /// Create a new builder for <see cref="ObservabilityConfig"/>.
        /// </summary>
        /// <param name="sdkKey">The LaunchDarkly SDK key used for authentication and resource attributes.</param>
        /// <returns>A new <see cref="Builder"/> instance for configuring observability.</returns>
        internal static Builder CreateBuilder(string sdkKey) => new Builder(sdkKey);

        /// <summary>
        /// Fluent builder for <see cref="ObservabilityConfig"/>.
        /// </summary>
        public sealed class Builder
        {
            private const string DefaultOtlpEndpoint = "https://otel.observability.app.launchdarkly.com:4318";
            private const string DefaultBackendUrl = "https://pub.observability.app.launchdarkly.com";
            private string _otlpEndpoint = DefaultOtlpEndpoint;
            private string _backendUrl = DefaultBackendUrl;
            private string _serviceName = string.Empty;
            private string _environment = string.Empty;
            private string _serviceVersion = string.Empty;
            private readonly string _sdkKey;

            internal Builder(string sdkKey)
            {
                this._sdkKey = sdkKey;   
            }

            /// <summary>
            /// Set the OTLP endpoint.
            /// <para>
            /// For most configurations, the OTLP endpoint will not need to be set.
            /// </para>
            /// <para>
            /// Setting the endpoint to null will reset the builder value to the default.
            /// </para>
            /// </summary>
            /// <param name="otlpEndpoint">The OTLP exporter endpoint URL.</param>
            /// <returns>A reference to this builder.</returns>
            public Builder WithOtlpEndpoint(string otlpEndpoint)
            {
                _otlpEndpoint = otlpEndpoint ?? DefaultOtlpEndpoint;
                return this;
            }

            /// <summary>
            /// Set the back-end URL for non-telemetry operations.
            /// <para>
            /// For most configurations, the backend url will not need to be set.
            /// </para>
            /// <para>
            /// Setting the url to null will reset the builder value to the default.
            /// </para>
            /// </summary>
            /// <param name="backendUrl">The back-end URL used for non-telemetry operations.</param>
            /// <returns>A reference to this builder.</returns>
            public Builder WithBackendUrl(string backendUrl)
            {
                _backendUrl = backendUrl ?? DefaultBackendUrl;
                return this;
            }

            /// <summary>
            /// Set the service name.
            /// </summary>
            /// <param name="serviceName">The logical service name used in telemetry resource attributes.</param>
            /// <returns>A reference to this builder.</returns>
            public Builder WithServiceName(string serviceName)
            {
                _serviceName = serviceName ?? string.Empty;
                return this;
            }

            /// <summary>
            /// Set the service version.
            /// </summary>
            /// <param name="serviceVersion">
            /// The version of the service that will be added to resource attributes when a service name is provided.
            /// </param>
            /// <returns>A reference to this builder.</returns>
            public Builder WithServiceVersion(string serviceVersion)
            {
                _serviceVersion =  serviceVersion ?? string.Empty;
                return this;
            }

            /// <summary>
            /// Set the environment name.
            /// </summary>
            /// <param name="environment">The environment name (for example, "prod" or "staging").</param>
            /// <returns>A reference to this builder.</returns>
            public Builder WithEnvironment(string environment)
            {
                _environment = environment ?? string.Empty;
                return this;
            }

            /// <summary>
            /// Build an immutable <see cref="ObservabilityConfig"/> instance.
            /// </summary>
            /// <returns>The constructed <see cref="ObservabilityConfig"/>.</returns>
            public ObservabilityConfig Build()
            {
                return new ObservabilityConfig(
                    _otlpEndpoint,
                    _backendUrl,
                    _serviceName,
                    _environment,
                    _serviceVersion,
                    _sdkKey);
            }
        }
    }
}