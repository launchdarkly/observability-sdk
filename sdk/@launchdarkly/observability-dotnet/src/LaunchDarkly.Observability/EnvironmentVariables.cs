namespace LaunchDarkly.Observability
{
    /// <summary>
    /// Contains constants for environment variable names used by the Observability SDK.
    /// </summary>
    internal static class EnvironmentVariables
    {
        /// <summary>
        /// The OpenTelemetry standard environment variable for the service name.
        /// When not explicitly set via WithServiceName(), this environment variable will be used.
        /// </summary>
        public const string OtelServiceName = "OTEL_SERVICE_NAME";

        /// <summary>
        /// The OpenTelemetry standard environment variable for the OTLP exporter endpoint.
        /// When not explicitly set via WithOtlpEndpoint(), this environment variable will be used.
        /// </summary>
        public const string OtelExporterOtlpEndpoint = "OTEL_EXPORTER_OTLP_ENDPOINT";
    }
}
