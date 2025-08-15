using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Threading.Tasks;
using LaunchDarkly.Logging;
using LaunchDarkly.Observability.Logging;
using LaunchDarkly.Observability.Otel;
using LaunchDarkly.Observability.Sampling;
using Microsoft.Extensions.DependencyInjection;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using OpenTelemetry;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;

namespace LaunchDarkly.Observability
{
    /// <summary>
    /// Static class containing extension methods for configuring observability
    /// </summary>
    public static class ObservabilityExtensions
    {
        // Used for metrics when a service name is not specified.
        private const string DefaultMetricsName = "launchdarkly-plugin-default-metrics";
        private const OtlpExportProtocol ExportProtocol = OtlpExportProtocol.HttpProtobuf;
        private const int FlushIntervalMs = 5 * 1000;
        private const int MaxExportBatchSize = 10000;
        private const int MaxQueueSize = 10000;
        private const int ExportTimeoutMs = 30000;

        private const string TracesPath = "/v1/traces";
        private const string LogsPath = "/v1/logs";
        private const string MetricsPath = "/v1/metrics";

        private static async Task GetSamplingConfigAsync(CustomSampler sampler, ObservabilityConfig config)
        {
            using (var samplingClient = new SamplingConfigClient(config.BackendUrl))
            {
                try
                {
                    var res = await samplingClient.GetSamplingConfigAsync(config.SdkKey).ConfigureAwait(false);
                    if (res == null) return;
                    sampler.SetConfig(res);
                }
                catch (Exception ex)
                {
                    DebugLogger.DebugLog($"Exception while getting sampling config: {ex}");
                }
            }
        }

        private static IEnumerable<KeyValuePair<string, object>> GetResourceAttributes(ObservabilityConfig config)
        {
            var attrs = new List<KeyValuePair<string, object>>();

            if (!string.IsNullOrWhiteSpace(config.Environment))
            {
                attrs.Add(new KeyValuePair<string, object>("deployment.environment.name", config.Environment));
            }

            attrs.Add(new KeyValuePair<string, object>("highlight.project_id", config.SdkKey));

            return attrs;
        }

        internal static void AddLaunchDarklyObservabilityWithConfig(this IServiceCollection services,
            ObservabilityConfig config, Logger logger = null)
        {
            DebugLogger.SetLogger(logger);
            var resourceAttributes = GetResourceAttributes(config);

            var sampler = new CustomSampler();

            // Asynchronously get sampling config.
            _ = Task.Run(() => GetSamplingConfigAsync(sampler, config));

            var resourceBuilder = ResourceBuilder.CreateDefault();
            if (!string.IsNullOrWhiteSpace(config.ServiceName))
            {
                resourceBuilder.AddService(config.ServiceName, serviceVersion: config.ServiceVersion);
                resourceBuilder.AddAttributes(resourceAttributes);
            }

            services.AddOpenTelemetry().WithTracing(tracing =>
            {
                tracing.SetResourceBuilder(resourceBuilder)
                    .AddHttpClientInstrumentation()
                    .AddGrpcClientInstrumentation()
                    .AddWcfInstrumentation()
                    .AddQuartzInstrumentation()
                    .AddAspNetCoreInstrumentation(options => { options.RecordException = true; })
                    .AddSqlClientInstrumentation(options => { options.SetDbStatementForText = true; });

                // Always use sampling exporter for traces
                var samplingTraceExporter = new SamplingTraceExporter(sampler, new OtlpExporterOptions
                {
                    Endpoint = new Uri(config.OtlpEndpoint + TracesPath),
                    Protocol = OtlpExportProtocol.HttpProtobuf,
                    BatchExportProcessorOptions = new BatchExportProcessorOptions<Activity>
                    {
                        MaxExportBatchSize = MaxExportBatchSize,
                        MaxQueueSize = MaxQueueSize,
                        ScheduledDelayMilliseconds = FlushIntervalMs,
                    }
                });

                tracing.AddProcessor(new BatchActivityExportProcessor(samplingTraceExporter, MaxQueueSize,
                    FlushIntervalMs, ExportTimeoutMs, MaxExportBatchSize));
            }).WithLogging(logging =>
            {
                logging.SetResourceBuilder(resourceBuilder)
                    .AddProcessor(new SamplingLogProcessor(sampler))
                    .AddOtlpExporter(options =>
                    {
                        options.Endpoint = new Uri(config.OtlpEndpoint + LogsPath);
                        options.Protocol = ExportProtocol;
                        options.BatchExportProcessorOptions.MaxExportBatchSize = MaxExportBatchSize;
                        options.BatchExportProcessorOptions.MaxQueueSize = MaxQueueSize;
                        options.BatchExportProcessorOptions.ScheduledDelayMilliseconds = FlushIntervalMs;
                    });
            }).WithMetrics(metrics =>
            {
                metrics.SetResourceBuilder(resourceBuilder)
                    .AddMeter(config.ServiceName ?? DefaultMetricsName)
                    .AddRuntimeInstrumentation()
                    .AddProcessInstrumentation()
                    .AddHttpClientInstrumentation()
                    .AddAspNetCoreInstrumentation()
                    .AddSqlClientInstrumentation()
                    .AddReader(new PeriodicExportingMetricReader(new OtlpMetricExporter(new OtlpExporterOptions
                    {
                        Endpoint = new Uri(config.OtlpEndpoint + MetricsPath),
                        Protocol = ExportProtocol
                    })));
            });
        }

        /// <summary>
        /// Add the LaunchDarkly Observability services. This function would typically be called by the LaunchDarkly
        /// Observability plugin. This should only be called by the end user if the Observability plugin needs to be
        /// initialized earlier than the LaunchDarkly client.
        /// </summary>
        /// <param name="services">The service collection</param>
        /// <param name="sdkKey">The LaunchDarkly SDK</param>
        /// <param name="configure">A method to configure the services</param>
        /// <returns>The service collection</returns>
        public static IServiceCollection AddLaunchDarklyObservability(
            this IServiceCollection services,
            string sdkKey,
            Action<ObservabilityConfig.ObservabilityConfigBuilder> configure)
        {
            var builder = ObservabilityConfig.Builder();
            configure(builder);

            var config = builder.Build(sdkKey);
            AddLaunchDarklyObservabilityWithConfig(services, config);
            return services;
        }
    }
}
