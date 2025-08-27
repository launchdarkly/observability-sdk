using System.Collections.Generic;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.Sdk.Server.Hooks;
using LaunchDarkly.Sdk.Server.Interfaces;
using LaunchDarkly.Sdk.Server.Plugins;
using LaunchDarkly.Sdk.Server.Telemetry;

// ReSharper disable once CheckNamespace
namespace LaunchDarkly.Observability
{
    public class ObservabilityPlugin : Plugin
    {
        private readonly ObservabilityPluginBuilder _config;

        /// <summary>
        /// Create a new builder for <see cref="ObservabilityPlugin"/>.
        /// <para>
        /// When using this builder, LaunchDarkly client must be constructed during Application_Start of your
        /// web application in Global.asax.cs.
        /// For example:
        ///
        /// <code>
        ///   protected void Application_Start()
        ///  {
        ///      // Other application specific code.
        ///      var client = new LdClient(Configuration.Builder(Environment.GetEnvironmentVariable("LAUNCHDARKLY_SDK_KEY"))
        ///          .Plugins(new PluginConfigurationBuilder().Add(ObservabilityPlugin.Builder()
        ///              .WithServiceName("classic-asp-application")
        ///              .Build()))
        ///          .Build());
        ///  }
        ///
        ///  protected void Application_End() {
        ///      Observe.Shutdown();
        ///  }
        /// </code>
        ///
        /// Additionally the Web.config must include the following.
        ///
        /// <code>
        ///  <system.webServer>
        ///    // Any existing content should remain and the following should be added.
        ///    <modules>
        ///    <add name="TelemetryHttpModule" type="OpenTelemetry.Instrumentation.AspNet.TelemetryHttpModule,
        ///      OpenTelemetry.Instrumentation.AspNet.TelemetryHttpModule" preCondition="integratedMode,managedHandler" />
        ///    </modules>
        ///  </system.webServer>
        /// </code>
        /// 
        /// </para>
        /// </summary>
        /// <returns>A new <see cref="ObservabilityPluginBuilder"/> instance for configuring the observability plugin.</returns>
        public static ObservabilityPluginBuilder Builder() =>
            new ObservabilityPluginBuilder();

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
