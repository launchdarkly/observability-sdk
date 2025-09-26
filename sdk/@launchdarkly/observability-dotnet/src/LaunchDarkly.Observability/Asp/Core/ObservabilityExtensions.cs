using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using LaunchDarkly.Observability.Otel;
using LaunchDarkly.Logging;
using LaunchDarkly.Observability.Logging;
using LaunchDarkly.Observability.Sampling;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using OpenTelemetry;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;

// ReSharper disable once CheckNamespace
namespace LaunchDarkly.Observability
{
    /// <summary>
    /// Static class containing extension methods for configuring observability
    /// </summary>
    public static class ObservabilityExtensions
    {
        private class LdObservabilityHostedService : IHostedService
        {
            private readonly ObservabilityConfig _config;
            private readonly ILoggerProvider _loggerProvider;

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

        internal static void AddLaunchDarklyObservabilityWithConfig(this IServiceCollection services,
            ObservabilityConfig config, Logger logger = null)
        {
            DebugLogger.SetLogger(logger);

            var sampler = CommonOtelOptions.GetSampler(config);

            var resourceBuilder = CommonOtelOptions.GetResourceBuilder(config);

            services.AddOpenTelemetry().WithTracing(tracing =>
            {
                tracing
                    .WithCommonLaunchDarklyConfig(config, resourceBuilder, sampler)
                    .AddAspNetCoreInstrumentation(options => { options.RecordException = true; });
            }).WithLogging(logging =>
            {
                logging.SetResourceBuilder(resourceBuilder)
                    .AddProcessor(new SamplingLogProcessor(sampler))
                    .AddOtlpExporter(options =>
                    {
                        options.WithCommonLaunchDarklyLoggingExport(config);
                    });
                config.ExtendedLoggerConfiguration?.Invoke(logging);
            }, options =>
            {
                options.IncludeFormattedMessage = true;
                config.ExtendedLoggerOptionsConfiguration?.Invoke(options);
            })
            .WithMetrics(metrics =>
            {
                metrics
                    .WithCommonLaunchDarklyConfig(config, resourceBuilder)
                    .AddAspNetCoreInstrumentation();
            });

            // Attach a hosted service which will allow us to get a logger provider instance from the built
            // service collection.
            services.AddHostedService((serviceProvider) =>
                new LdObservabilityHostedService(config, serviceProvider));
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
