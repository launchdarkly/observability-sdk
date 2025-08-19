using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;
using LaunchDarkly.Observability.Otel;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
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
        private const OtlpExportProtocol ExportProtocol = OtlpExportProtocol.HttpProtobuf;
        private const int FlushIntervalMs = 5 * 1000;
        private const int MaxExportBatchSize = 10000;
        private const int MaxQueueSize = 10000;

        private const string TracesPath = "/v1/traces";
        private const string LogsPath = "/v1/logs";
        private const string MetricsPath = "/v1/metrics";

        private class LdObservabilityHostedService : IHostedService
        {
            private ObservabilityConfig _config;
            private ActivitySource _activitySource;
            private ILoggerProvider _loggerProvider;

            public LdObservabilityHostedService(ObservabilityConfig config, IServiceProvider provider)
            {
                _loggerProvider = provider.GetService<ILoggerProvider>();
                _config = config;
            }
            public Task StartAsync(CancellationToken cancellationToken)
            {
                Observe.Initialize(_config, _loggerProvider);
                return Task.CompletedTask;
            }

            public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;
        }

        private static IEnumerable<KeyValuePair<string, object>> GetResourceAttributes(ObservabilityConfig config)
        {
            var attrs = new List<KeyValuePair<string, object>>();

            if (!string.IsNullOrWhiteSpace(config.Environment))
            {
                attrs.Add(new KeyValuePair<string, object>(AttributeNames.DeploymentEnvironment, config.Environment));
            }

            attrs.Add(new KeyValuePair<string, object>(AttributeNames.ProjectId, config.SdkKey));

            return attrs;
        }

        internal static void AddLaunchDarklyObservabilityWithConfig(this IServiceCollection services,
            ObservabilityConfig config)
        {
            var resourceAttributes = GetResourceAttributes(config);

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
                    .AddSqlClientInstrumentation(options => { options.SetDbStatementForText = true; })
                    .AddSource(config.ServiceName ?? DefaultNames.ActivitySourceName)
                    .AddOtlpExporter(options =>
                    {
                        options.Endpoint = new Uri(config.OtlpEndpoint + TracesPath);
                        options.Protocol = ExportProtocol;
                        options.BatchExportProcessorOptions.MaxExportBatchSize = MaxExportBatchSize;
                        options.BatchExportProcessorOptions.MaxQueueSize = MaxQueueSize;
                        options.BatchExportProcessorOptions.ScheduledDelayMilliseconds = FlushIntervalMs;
                    });
            }).WithLogging(logging =>
            {
                logging.SetResourceBuilder(resourceBuilder)
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
                    .AddMeter(config.ServiceName ?? DefaultNames.MeterName)
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
            
            // Attach a hosted service which will allow us to get a logger provider instance from the built
            // serice collection.
            services.AddHostedService((serviceProvider) => new  LdObservabilityHostedService(config, serviceProvider));
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
