using System;

namespace LaunchDarkly.Observability
{
    /// <summary>
    /// Base builder which allows for methods to be shared between building a config directly and building a plugin.
    /// <remarks>
    /// This uses the CRTP pattern to allow the individual builder methods to return instances of the derived builder
    /// type.
    /// </remarks>
    /// </summary>
    public class BaseBuilder<TBuilder> where TBuilder : BaseBuilder<TBuilder>
    {
        private const string DefaultOtlpEndpoint = "https://otel.observability.app.launchdarkly.com:4318";
        private const string DefaultBackendUrl = "https://pub.observability.app.launchdarkly.com";
        private string _otlpEndpoint = DefaultOtlpEndpoint;
        private string _backendUrl = DefaultBackendUrl;
        private string _serviceName = string.Empty;
        private string _environment = string.Empty;
        private string _serviceVersion = string.Empty;

        protected BaseBuilder()
        {
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
        public TBuilder WithOtlpEndpoint(string otlpEndpoint)
        {
            _otlpEndpoint = otlpEndpoint ?? DefaultOtlpEndpoint;
            return (TBuilder)this;
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
        public TBuilder WithBackendUrl(string backendUrl)
        {
            _backendUrl = backendUrl ?? DefaultBackendUrl;
            return (TBuilder)this;
        }

        /// <summary>
        /// Set the service name.
        /// </summary>
        /// <param name="serviceName">The logical service name used in telemetry resource attributes.</param>
        /// <returns>A reference to this builder.</returns>
        public TBuilder WithServiceName(string serviceName)
        {
            _serviceName = serviceName ?? string.Empty;
            return (TBuilder)this;
        }

        /// <summary>
        /// Set the service version.
        /// </summary>
        /// <param name="serviceVersion">
        /// The version of the service that will be added to resource attributes when a service name is provided.
        /// </param>
        /// <returns>A reference to this builder.</returns>
        public TBuilder WithServiceVersion(string serviceVersion)
        {
            _serviceVersion = serviceVersion ?? string.Empty;
            return (TBuilder)this;
        }

        /// <summary>
        /// Set the environment name.
        /// </summary>
        /// <param name="environment">The environment name (for example, "prod" or "staging").</param>
        /// <returns>A reference to this builder.</returns>
        public TBuilder WithEnvironment(string environment)
        {
            _environment = environment ?? string.Empty;
            return (TBuilder)this;
        }

        /// <summary>
        /// Build an immutable <see cref="ObservabilityConfig"/> instance.
        /// </summary>
        /// <returns>The constructed <see cref="ObservabilityConfig"/>.</returns>
        internal ObservabilityConfig BuildConfig(string sdkKey)
        {
            if (sdkKey == null)
            {
                throw new ArgumentNullException(nameof(sdkKey),
                    "SDK key cannot be null when creating an ObservabilityConfig builder.");
            }

            return new ObservabilityConfig(
                _otlpEndpoint,
                _backendUrl,
                _serviceName,
                _environment,
                _serviceVersion,
                sdkKey);
        }
    }
}
