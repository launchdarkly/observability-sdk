using NUnit.Framework;
using System;
using Microsoft.Extensions.DependencyInjection;
#if !NETFRAMEWORK
using OpenTelemetry.Logs;
#endif

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class ObservabilityPluginBuilderTests
    {
        private IServiceCollection _services;

        [SetUp]
        public void SetUp()
        {
            _services = new ServiceCollection();
        }

        [Test]
        public void CreateBuilder_WithValidParameters_CreatesBuilder()
        {
            var builder = ObservabilityPlugin.Builder(_services);

            Assert.That(builder, Is.Not.Null);
            Assert.That(builder, Is.InstanceOf<ObservabilityPlugin.ObservabilityPluginBuilder>());
        }

        [Test]
        public void CreateBuilder_WithNullServices_ThrowsArgumentNullException()
        {
            var exception = Assert.Throws<ArgumentNullException>(() =>
                ObservabilityPlugin.Builder(null));
            Assert.Multiple(() =>
            {
                Assert.That(exception, Is.Not.Null);
                Assert.That(exception.ParamName, Is.EqualTo("services"));
                Assert.That(exception.Message,
                    Does.Contain("Service collection cannot be null when creating an ObservabilityPlugin builder"));
            });
        }

        [Test]
        public void Build_WithAllFields_CreatesPluginWithConfiguration()
        {
            var plugin = ObservabilityPlugin.Builder(_services)
                .WithOtlpEndpoint("https://otlp.example.com")
                .WithBackendUrl("https://backend.example.com")
                .WithServiceName("service-a")
                .WithServiceVersion("1.0.0")
                .WithEnvironment("prod")
                .Build();

            Assert.That(plugin, Is.InstanceOf<ObservabilityPlugin>());
        }

        [Test]
        public void Build_WithNullValues_HandlesNullsCorrectly()
        {
            var plugin = ObservabilityPlugin.Builder(_services)
                .WithOtlpEndpoint(null)
                .WithBackendUrl(null)
                .WithServiceName(null)
                .WithServiceVersion(null)
                .WithEnvironment(null)
                .Build();

            Assert.That(plugin, Is.InstanceOf<ObservabilityPlugin>());
        }

        [Test]
        public void Build_UsesOtelServiceNameEnvironmentVariable_WhenServiceNameNotSet()
        {
            // Save the original environment variable value
            var originalValue = Environment.GetEnvironmentVariable(EnvironmentVariables.OtelServiceName);

            try
            {
                // Set the environment variable
                Environment.SetEnvironmentVariable(EnvironmentVariables.OtelServiceName, "plugin-service-from-env");

                // Build plugin without setting service name explicitly
                var plugin = ObservabilityPlugin.Builder(_services).Build();

                // Plugin should be created successfully and will use the env var internally
                Assert.That(plugin, Is.InstanceOf<ObservabilityPlugin>());
            }
            finally
            {
                // Restore the original environment variable value
                Environment.SetEnvironmentVariable(EnvironmentVariables.OtelServiceName, originalValue);
            }
        }

        [Test]
        public void Build_PrefersExplicitServiceName_OverEnvironmentVariable()
        {
            // Save the original environment variable value
            var originalValue = Environment.GetEnvironmentVariable(EnvironmentVariables.OtelServiceName);

            try
            {
                // Set the environment variable
                Environment.SetEnvironmentVariable(EnvironmentVariables.OtelServiceName, "plugin-service-from-env");

                // Build plugin with explicit service name
                var plugin = ObservabilityPlugin.Builder(_services)
                    .WithServiceName("explicit-plugin-service")
                    .Build();

                // Plugin should be created successfully and will use the explicit value internally
                Assert.That(plugin, Is.InstanceOf<ObservabilityPlugin>());
            }
            finally
            {
                // Restore the original environment variable value
                Environment.SetEnvironmentVariable(EnvironmentVariables.OtelServiceName, originalValue);
            }
        }

#if !NETFRAMEWORK
        [Test]
        public void WithExtendedLoggerConfiguration_WithBothParameters_SetsConfigurationCorrectly()
        {
            Action<LoggerProviderBuilder> loggerConfig = _ => { };
            Action<OpenTelemetryLoggerOptions> loggerOptions = _ => { };

            var pluginBuilder = ObservabilityPlugin.Builder(_services)
                .WithExtendedLoggerConfiguration(loggerConfig, loggerOptions);

            var plugin = pluginBuilder.Build();

            Assert.That(plugin, Is.Not.Null);
            Assert.That(plugin, Is.InstanceOf<ObservabilityPlugin>());
        }

        [Test]
        public void WithExtendedLoggerConfiguration_WithNullLoggerConfiguration_AcceptsNullValue()
        {
            Action<OpenTelemetryLoggerOptions> loggerOptions = _ => { };

            var pluginBuilder = ObservabilityPlugin.Builder(_services)
                .WithExtendedLoggerConfiguration(null, loggerOptions);

            var plugin = pluginBuilder.Build();

            Assert.That(plugin, Is.Not.Null);
            Assert.That(plugin, Is.InstanceOf<ObservabilityPlugin>());
        }

        [Test]
        public void WithExtendedLoggerConfiguration_WithNullLoggerOptions_AcceptsNullValue()
        {
            Action<LoggerProviderBuilder> loggerConfig = _ => { };

            var pluginBuilder = ObservabilityPlugin.Builder(_services)
                .WithExtendedLoggerConfiguration(loggerConfig, null);

            var plugin = pluginBuilder.Build();

            Assert.That(plugin, Is.Not.Null);
            Assert.That(plugin, Is.InstanceOf<ObservabilityPlugin>());
        }

        [Test]
        public void WithExtendedLoggerConfiguration_WithBothParametersNull_AcceptsNullValues()
        {
            var pluginBuilder = ObservabilityPlugin.Builder(_services)
                .WithExtendedLoggerConfiguration(null, null);

            var plugin = pluginBuilder.Build();

            Assert.That(plugin, Is.Not.Null);
            Assert.That(plugin, Is.InstanceOf<ObservabilityPlugin>());
        }

        [Test]
        public void WithExtendedLoggerConfiguration_ReturnsBuilderInstance()
        {
            Action<LoggerProviderBuilder> loggerConfig = _ => { };
            Action<OpenTelemetryLoggerOptions> loggerOptions = _ => { };

            var result = ObservabilityPlugin.Builder(_services)
                .WithExtendedLoggerConfiguration(loggerConfig, loggerOptions);

            Assert.That(result, Is.Not.Null);
            Assert.That(result, Is.InstanceOf<ObservabilityPlugin.ObservabilityPluginBuilder>());
        }
#endif
    }
}
