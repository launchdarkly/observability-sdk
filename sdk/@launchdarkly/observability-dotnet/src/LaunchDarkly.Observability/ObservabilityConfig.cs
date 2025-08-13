using System;

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

        internal ObservabilityConfig(
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
        /// <returns>A new <see cref="ObservabilityConfigBuilder"/> instance for configuring observability.</returns>
        internal static ObservabilityConfigBuilder Builder() => new ObservabilityConfigBuilder();

        /// <summary>
        /// Builder for building an observability configuration.
        /// </summary>
        public class ObservabilityConfigBuilder : BaseBuilder<ObservabilityConfigBuilder>
        {
            internal ObservabilityConfigBuilder()
            {
            }

            internal ObservabilityConfig Build(string sdkKey)
            {
                return BuildConfig(sdkKey);
            }
        }
    }
}
