using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using LaunchDarkly.Observability.Logging;
using LaunchDarkly.Observability.Sampling;
using LaunchDarkly.Sdk.Internal.Concurrent;
using OpenTelemetry;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

namespace LaunchDarkly.Observability.Otel
{
    internal static class CommonOtelOptions
    {
        private const OtlpExportProtocol ExportProtocol = OtlpExportProtocol.HttpProtobuf;
        private const int FlushIntervalMs = 5 * 1000;
        private const int MaxExportBatchSize = 10000;
        private const int MaxQueueSize = 10000;
        private const int ExportTimeoutMs = 30000;

        private const string TracesPath = "/v1/traces";
        private const string LogsPath = "/v1/logs";
        private const string MetricsPath = "/v1/metrics";

        /// <summary>
        /// Atomic boolean used to track if the sampling configuration has been fetched.
        /// </summary>
        private static readonly AtomicBoolean SamplerConfigFetched = new AtomicBoolean(false);

        /// <summary>
        /// There is a static sampler instance, but we required this instances be passed to methods.
        /// This ensures that the sampler is always accessed in a way that results in its config
        /// being fetched.
        /// </summary>
        private static readonly CustomSampler Sampler = new CustomSampler();

        private static async Task GetSamplingConfigAsync(ObservabilityConfig config)
        {
            using (var samplingClient = new SamplingConfigClient(config.BackendUrl))
            {
                try
                {
                    var res = await samplingClient.GetSamplingConfigAsync(config.SdkKey).ConfigureAwait(false);
                    if (res == null) return;
                    Sampler.SetConfig(res);
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
                attrs.Add(
                    new KeyValuePair<string, object>(AttributeNames.DeploymentEnvironment, config.Environment));
            }

            attrs.Add(new KeyValuePair<string, object>(AttributeNames.ProjectId, config.SdkKey));

            return attrs;
        }

        private static void FetchSamplingConfig(ObservabilityConfig config)
        {
            var previous = SamplerConfigFetched.GetAndSet(true);
            if (!previous)
            {
                _ = Task.Run(() => GetSamplingConfigAsync(config));
            }
        }

        public static CustomSampler GetSampler(ObservabilityConfig config)
        {
            FetchSamplingConfig(config);
            return Sampler;
        }

        public static ResourceBuilder GetResourceBuilder(ObservabilityConfig config)
        {
            var resourceBuilder = ResourceBuilder.CreateDefault();
            if (string.IsNullOrWhiteSpace(config.ServiceName)) return resourceBuilder;
            resourceBuilder.AddService(config.ServiceName, serviceVersion: config.ServiceVersion);
            resourceBuilder.AddAttributes(GetResourceAttributes(config));
            return resourceBuilder;
        }


        public static TracerProviderBuilder WithCommonLaunchDarklyConfig(this TracerProviderBuilder builder,
            ObservabilityConfig config, ResourceBuilder resourceBuilder, CustomSampler sampler)
        {
            var samplingTraceExporter = new SamplingTraceExporter(sampler, new OtlpExporterOptions
            {
                Endpoint = new Uri(config.OtlpEndpoint + TracesPath),
                Protocol = OtlpExportProtocol.HttpProtobuf,
                BatchExportProcessorOptions =
                {
                    MaxExportBatchSize = MaxExportBatchSize,
                    MaxQueueSize = MaxQueueSize,
                    ScheduledDelayMilliseconds = FlushIntervalMs
                }
            });

            builder.SetResourceBuilder(resourceBuilder)
                .AddHttpClientInstrumentation()
                .AddGrpcClientInstrumentation()
                .AddWcfInstrumentation()
                .AddQuartzInstrumentation()
                .AddSqlClientInstrumentation(options => { options.SetDbStatementForText = true; })
                .AddSource(DefaultNames.ActivitySourceNameOrDefault(config.ServiceName))
                .AddProcessor(new BatchActivityExportProcessor(samplingTraceExporter, MaxQueueSize,
                    FlushIntervalMs, ExportTimeoutMs, MaxExportBatchSize));

            config.ExtendedTracerConfiguration?.Invoke(builder);
            return builder;
        }

        public static MeterProviderBuilder WithCommonLaunchDarklyConfig(this MeterProviderBuilder builder,
            ObservabilityConfig config, ResourceBuilder resourceBuilder)
        {
            builder.SetResourceBuilder(resourceBuilder)
                .AddMeter(DefaultNames.MeterNameOrDefault(config.ServiceName))
                .AddRuntimeInstrumentation()
                .AddProcessInstrumentation()
                .AddHttpClientInstrumentation()
                .AddSqlClientInstrumentation()
                .AddReader(new PeriodicExportingMetricReader(new OtlpMetricExporter(new OtlpExporterOptions
                {
                    Endpoint = new Uri(config.OtlpEndpoint + MetricsPath),
                    Protocol = ExportProtocol
                })));
            config.ExtendedMeterConfiguration?.Invoke(builder);
            return builder;
        }

        public static void WithCommonLaunchDarklyLoggingExport(this OtlpExporterOptions options,
            ObservabilityConfig config)
        {
            options.Endpoint = new Uri(config.OtlpEndpoint + LogsPath);
            options.Protocol = ExportProtocol;
            options.BatchExportProcessorOptions.MaxExportBatchSize = MaxExportBatchSize;
            options.BatchExportProcessorOptions.MaxQueueSize = MaxQueueSize;
            options.BatchExportProcessorOptions.ScheduledDelayMilliseconds = FlushIntervalMs;
        }
    }
}
