using LaunchDarkly.Logging;
using LaunchDarkly.Observability.Otel;
using Microsoft.Extensions.Logging;
using OpenTelemetry;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;

// ReSharper disable once CheckNamespace
namespace LaunchDarkly.Observability
{
    public static class OpenTelemetry
    {
        private static TracerProvider _tracerProvider;
        private static MeterProvider _meterProvider;
        private static readonly object ProviderLock = new object();

        /// <summary>
        /// Extension method which adds LaunchDarkly logging to a logging factory.
        ///
        /// <code>
        /// using (var factory = LoggerFactory.Create(builder => { builder.AddLaunchDarklyLogging(config); })) {
        ///   // Use factory to get logger which uses LaunchDarkly loging.
        /// }
        /// </code>
        /// </summary>
        /// <param name="loggingBuilder">the logging builder for the factory</param>
        /// <param name="config">the LaunchDarkly observability configuration</param>
        public static void AddLaunchDarklyLogging(this ILoggingBuilder loggingBuilder, ObservabilityConfig config)
        {
            loggingBuilder.AddOpenTelemetry(options =>
            {
                options.SetResourceBuilder(CommonOtelOptions.GetResourceBuilder(config))
                    .AddProcessor(new SamplingLogProcessor(CommonOtelOptions.GetSampler(config)))
                    .AddOtlpExporter(exportOptions => { exportOptions.WithCommonLaunchDarklyLoggingExport(config); });
            });
        }

        /// <summary>
        /// Configure LaunchDarkly observability. In typical usage, the ObservabilityPlugin should be used instead.
        /// <para>
        /// This method is for advanced use-cases where the LaunchDarkly client needs to be initialized later than the
        /// telemetry implementation.
        /// </para>
        /// <para>
        /// If this method is used, then the <see cref="ObservabilityPlugin"/> should be instantiated using
        /// <see cref="ObservabilityPlugin.ForExistingServices"/> method.
        /// </para>
        /// </summary>
        /// <param name="config">configuration for LaunchDarkly Observability</param>
        /// <param name="debugLogger">an optional debug logger</param>
        public static void Register(ObservabilityConfig config, Logger debugLogger = null)
        {
            lock (ProviderLock)
            {
                // If the providers are set, then the implementation has already been configured.
                if (_tracerProvider != null)
                {
                    return;
                }

                var resourceBuilder = CommonOtelOptions.GetResourceBuilder(config);
                var sampler = CommonOtelOptions.GetSampler(config);

                _tracerProvider = global::OpenTelemetry.Sdk.CreateTracerProviderBuilder()
                    .WithCommonLaunchDarklyConfig(config, resourceBuilder, sampler)
                    .AddAspNetInstrumentation()
                    .Build();

                _meterProvider = global::OpenTelemetry.Sdk.CreateMeterProviderBuilder()
                    .WithCommonLaunchDarklyConfig(config, resourceBuilder)
                    .AddAspNetInstrumentation()
                    .Build();
            }

            using (var factory = LoggerFactory.Create(builder => { builder.AddLaunchDarklyLogging(config); }))
            {
                var logger = factory.CreateLogger<ObservabilityPlugin>();
                Observe.Initialize(config, logger);
            }
        }

        /// <summary>
        /// Shutdown the underlying telemetry.
        /// </summary>
        public static void Shutdown()
        {
            lock (ProviderLock)
            {
                _tracerProvider?.Dispose();
                _meterProvider?.Dispose();
                _tracerProvider = null;
                _meterProvider = null;
            }
        }
    }
}
