using System;
using System.Diagnostics.Metrics;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;

namespace LaunchDarkly.Observability
{
#if NETFRAMEWORK
    using LoggerBuilderType = OpenTelemetryLoggerOptions;
#else
    using LoggerBuilderType = LoggerProviderBuilder;
#endif

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
        private string _otlpEndpoint = string.Empty;
        private string _backendUrl = string.Empty;
        private string _serviceName = string.Empty;
        private string _environment = string.Empty;
        private string _serviceVersion = string.Empty;
        private Action<TracerProviderBuilder> _extendedTracerConfiguration;
        private Action<LoggerBuilderType> _extendedLoggerConfiguration;
        private Action<MeterProviderBuilder> _extendedMeterConfiguration;
#if !NETFRAMEWORK
        protected Action<OpenTelemetryLoggerOptions> ConfigureLoggerOptions;
#endif

        protected BaseBuilder()
        {
        }

        /// <summary>
        /// Set the OTLP endpoint.
        /// <para>
        /// For most configurations, the OTLP endpoint will not need to be set.
        /// </para>
        /// <para>
        /// If not explicitly set, set to null, or set to whitespace/empty string, the OTLP endpoint will be read from
        /// the OTEL_EXPORTER_OTLP_ENDPOINT environment variable. Values set with this method take precedence over the
        /// environment variable.
        /// </para>
        /// <para>
        /// Setting the endpoint to null will reset the builder value to the default.
        /// </para>
        /// </summary>
        /// <param name="otlpEndpoint">The OTLP exporter endpoint URL.</param>
        /// <returns>A reference to this builder.</returns>
        public TBuilder WithOtlpEndpoint(string otlpEndpoint)
        {
            _otlpEndpoint = otlpEndpoint;
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
            _backendUrl = backendUrl;
            return (TBuilder)this;
        }

        /// <summary>
        /// Set the service name.
        /// <para>
        /// If not explicitly set, set to null, or set to whitespace/empty string, the service name will be read from
        /// the OTEL_SERVICE_NAME environment variable. Values set with this method take precedence over the environment
        /// variable.
        /// </para>
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
        /// Method which extends the configuration of the tracer provider.
        /// <para>
        /// The basic tracer options will already be configured by the LaunchDarkly Observability plugin. This method
        /// should be used to extend that configuration with additional instrumentation or additional activity sources.
        /// </para>
        /// <para>
        /// By default tracing will be configured with the following instrumentation:
        /// <list type="bullet">
        /// <item>HTTP Client Instrumentation</item>
        /// <item>GRPC Client Instrumentation</item>
        /// <item>WCF Instrumentation</item>
        /// <item>Quart Instrumentation</item>
        /// <item>AspNetCore Instrumentation</item>
        /// <item>SQL Client Instrumentation</item>
        /// </list>
        /// </para>
        /// <para>
        /// Configuring exporters or processors may interfere with the operation of the plugin and is not recommended.
        /// </para>
        /// </summary>
        /// <example>
        /// Add additional activity sources:
        /// <code>
        /// ObservabilityConfig.Builder()
        ///     .WithExtendedTracingConfig(builder =>
        ///     {
        ///         // Activities started by this activity source will be in exported spans.
        ///         builder.AddSource("my-custom-activity-source");
        ///     });
        /// </code>
        /// </example>
        /// <example>
        /// Add additional instrumentation.
        /// <code>
        /// ObservabilityConfig.Builder()
        ///     .WithExtendedTracingConfig(builder =>
        ///     {
        ///         builder.AddMyInstrumentation()
        ///     });
        /// </code>
        /// </example>
        /// <param name="extendedTracerConfiguration">A function used to extend the tracing configuration.</param>
        /// <returns>A reference to this builder.</returns>
        public TBuilder WithExtendedTracingConfig(Action<TracerProviderBuilder> extendedTracerConfiguration)
        {
            _extendedTracerConfiguration = extendedTracerConfiguration;
            return (TBuilder)this;
        }

        /// <summary>
        /// Method which extends the configuration of the logger provider.
        /// <para>
        /// The basic logger options will already be configured by the LaunchDarkly Observability plugin. This method
        /// should be used to extend that configuration with additional instrumentation.
        /// </para>
        /// <para>
        /// Configuring exporters or processors may interfere with the operation of the plugin and is not recommended.
        /// </para>
        /// </summary>
        /// <example>
        /// Adding custom instrumentation:
        /// <code>
        /// ObservabilityConfig.Builder()
        ///     .WithExtendedLoggerConfiguration(builder =>
        ///     {
        ///         builder.AddMyInstrumentation();
        ///     });
        /// </code>
        /// </example>
        /// <param name="extendedLoggerConfiguration">A function used to extend the logging configuration.</param>
        /// <returns>A reference to this builder.</returns>
        public TBuilder WithExtendedLoggerConfiguration(Action<LoggerBuilderType> extendedLoggerConfiguration)
        {
            _extendedLoggerConfiguration = extendedLoggerConfiguration;
            return (TBuilder)this;
        }

        /// <summary>
        /// Method which extends the configuration of the meter provider.
        /// <para>
        /// The basic meter options will already be configured by the LaunchDarkly Observability plugin. This method
        /// should be used to extend that configuration with additional meters, views, or custom instrumentation.
        /// </para>
        /// <para>
        /// By default, metrics will be configured with the following instrumentation:
        /// <list type="bullet">
        /// <item>Runtime Instrumentation (GC, thread pool, JIT statistics)</item>
        /// <item>Process Instrumentation (CPU, memory, handle counts)</item>
        /// <item>HTTP Client Instrumentation (request counts, durations)</item>
        /// <item>AspNetCore Instrumentation (request rates, response times)</item>
        /// <item>SQL Client Instrumentation (query execution times, connection pool metrics)</item>
        /// </list>
        /// </para>
        /// <para>
        /// Configuring exporters or processors may interfere with the operation of the plugin and is not recommended.
        /// </para>
        /// </summary>
        /// <example>
        /// Adding custom instrumentation:
        /// <code>
        /// ObservabilityConfig.Builder()
        ///     .WithExtendedMeterConfiguration(builder =>
        ///     {
        ///         // Add meters from your custom instrumentation
        ///         builder.AddMyInstrumentation();
        ///     });
        /// </code>
        /// </example>
        /// <param name="extendedMeterConfiguration">A function used to extend the metrics configuration.</param>
        /// <returns>A reference to this builder.</returns>
        public TBuilder WithExtendedMeterConfiguration(Action<MeterProviderBuilder> extendedMeterConfiguration)
        {
            _extendedMeterConfiguration = extendedMeterConfiguration;
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

            var effectiveServiceName = EnvironmentHelper.GetValueOrEnvironment(
                _serviceName,
                EnvironmentVariables.OtelServiceName,
                string.Empty);

            var effectiveOtlpEndpoint = EnvironmentHelper.GetValueOrEnvironment(
                _otlpEndpoint,
                EnvironmentVariables.OtelExporterOtlpEndpoint,
                DefaultOtlpEndpoint);

            var effectiveBackendUrl = string.IsNullOrWhiteSpace(_backendUrl) ? DefaultBackendUrl : _backendUrl;

            return new ObservabilityConfig(
                effectiveOtlpEndpoint,
                effectiveBackendUrl,
                effectiveServiceName,
                _environment,
                _serviceVersion,
                sdkKey,
                _extendedTracerConfiguration,
                _extendedLoggerConfiguration,
                _extendedMeterConfiguration
#if !NETFRAMEWORK
                , ConfigureLoggerOptions
#endif
            );
        }
    }
}
