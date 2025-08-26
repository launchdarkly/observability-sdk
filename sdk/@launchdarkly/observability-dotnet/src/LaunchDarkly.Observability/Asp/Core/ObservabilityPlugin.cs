using System;
using System.Collections.Generic;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.Sdk.Server.Hooks;
using LaunchDarkly.Sdk.Server.Interfaces;
using LaunchDarkly.Sdk.Server.Plugins;
using LaunchDarkly.Sdk.Server.Telemetry;
using Microsoft.Extensions.DependencyInjection;
using OpenTelemetry.Logs;

// ReSharper disable once CheckNamespace
namespace LaunchDarkly.Observability
{
    public class ObservabilityPlugin : Plugin
    {
        private readonly ObservabilityPluginBuilder _config;
        private readonly IServiceCollection _services;

        /// <summary>
        /// Construct a plugin which is intended to be used with already configured observability services.
        /// <para>
        /// In a typical configuration, this method will not need to be used.
        /// </para>
        /// <para>
        /// This method only needs to be used when observability related functionality must be initialized before it
        /// is possible to initialize the LaunchDarkly SDK.
        /// </para>
        /// </summary>
        /// <returns>an observability plugin instance</returns>
        public static ObservabilityPlugin ForExistingServices() => new ObservabilityPlugin();

        /// <summary>
        /// Create a new builder for <see cref="ObservabilityPlugin"/>.
        /// <para>
        /// When using this builder, LaunchDarkly client must be constructed before your application is built.
        /// For example:
        ///
        /// <code>
        /// var builder = WebApplication.CreateBuilder(args);
        ///
        ///
        /// var config = Configuration.Builder(Environment.GetEnvironmentVariable("your-sdk-key")
        ///     .Plugins(new PluginConfigurationBuilder()
        ///         .Add(ObservabilityPlugin.Builder(builder.Services)
        ///             .WithServiceName("ryan-test-service")
        ///             .WithServiceVersion("0.0.0")
        ///             .Build())).Build();
        /// // Building the LdClient with the Observability plugin. This line will add services to the web application.
        /// var client = new LdClient(config);
        ///
        /// // Client must be built before this line.
        /// var app = builder.Build();
        /// </code>
        /// </para>
        /// </summary>
        /// <param name="services">The service collection for dependency injection.</param>
        /// <returns>A new <see cref="ObservabilityPluginBuilder"/> instance for configuring the observability plugin.</returns>
        public static ObservabilityPluginBuilder Builder(IServiceCollection services) =>
            new ObservabilityPluginBuilder(services);

        internal ObservabilityPlugin(IServiceCollection services, ObservabilityPluginBuilder config) : base(
            "LaunchDarkly.Observability")
        {
            _config = config;
            _services = services;
        }

        internal ObservabilityPlugin() : base("LaunchDarkly.Observability")
        {
            _services = null;
            _config = null;
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            if (_services == null || _config == null) return;
            var config = _config.BuildConfig(metadata.Credential);
            _services.AddLaunchDarklyObservabilityWithConfig(config, client.GetLogger());
        }

        /// <inheritdoc />
        public override IList<Hook> GetHooks(EnvironmentMetadata metadata)
        {
            return new List<Hook>
            {
                TracingHook.Builder().IncludeValue().Build()
            };
        }

        /// <summary>
        /// Used to build an instance of the Observability Plugin.
        /// </summary>
        public sealed class ObservabilityPluginBuilder : BaseBuilder<ObservabilityPluginBuilder>
        {
            private readonly IServiceCollection _services;

            internal ObservabilityPluginBuilder(IServiceCollection services) : base()
            {
                _services = services ?? throw new ArgumentNullException(nameof(services),
                    "Service collection cannot be null when creating an ObservabilityPlugin builder.");
            }

            /// <summary>
            /// Build an <see cref="ObservabilityPlugin"/> instance with the configured settings.
            /// </summary>
            /// <returns>The constructed <see cref="ObservabilityPlugin"/>.</returns>
            public ObservabilityPlugin Build()
            {
                return new ObservabilityPlugin(_services, this);
            }
        }
    }
}
