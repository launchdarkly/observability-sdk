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

        /// <summary>
        /// Create a new builder for <see cref="ObservabilityPlugin"/>.
        /// <para>
        /// When using this builder, LaunchDarkly client must be constructed before your application is built.
        /// For example:
        ///
        /// <code>
        /// TODO: Add example.
        /// </code>
        /// </para>
        /// </summary>
        /// <returns>A new <see cref="ObservabilityPluginBuilder"/> instance for configuring the observability plugin.</returns>
        public static ObservabilityPluginBuilder Builder() =>
            new ObservabilityPluginBuilder();

        internal ObservabilityPlugin(ObservabilityPluginBuilder config) : base(
            "LaunchDarkly.Observability")
        {
            _config = config;
        }

        internal ObservabilityPlugin() : base("LaunchDarkly.Observability")
        {
            _config = null;
        }

        /// <inheritdoc />
        public override void Register(ILdClient client, EnvironmentMetadata metadata)
        {
            if (_config == null) return;
            var config = _config.BuildConfig(metadata.Credential);
            OpenTelemetry.Register(config);
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
            /// <summary>
            /// Build an <see cref="ObservabilityPlugin"/> instance with the configured settings.
            /// </summary>
            /// <returns>The constructed <see cref="ObservabilityPlugin"/>.</returns>
            public ObservabilityPlugin Build()
            {
                return new ObservabilityPlugin(this);
            }
        }
    }
}
