using System;
using LaunchDarkly.Observability;
using NUnit.Framework;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class ObservabilityConfigBuilderTests
    {
        [Test]
        public void Build_WithAllFields_SetsValues()
        {
            var config = ObservabilityConfig.Builder()
                .WithOtlpEndpoint("https://otlp.example.com")
                .WithBackendUrl("https://backend.example.com")
                .WithServiceName("service-a")
                .WithServiceVersion("1.0.0")
                .WithEnvironment("prod")
                .Build("sdk-123");

            Assert.Multiple(() =>
            {
                Assert.That(config.OtlpEndpoint, Is.EqualTo("https://otlp.example.com"));
                Assert.That(config.BackendUrl, Is.EqualTo("https://backend.example.com"));
                Assert.That(config.ServiceName, Is.EqualTo("service-a"));
                Assert.That(config.ServiceVersion, Is.EqualTo("1.0.0"));
                Assert.That(config.Environment, Is.EqualTo("prod"));
                Assert.That(config.SdkKey, Is.EqualTo("sdk-123"));
            });
        }

        [Test]
        public void Build_WithoutSettingFields_UsesDefaults()
        {
            var config = ObservabilityConfig.Builder().Build("sdk-xyz");

            Assert.Multiple(() =>
            {
                Assert.That(config.OtlpEndpoint, Is.EqualTo("https://otel.observability.app.launchdarkly.com:4318"));
                Assert.That(config.BackendUrl, Is.EqualTo("https://pub.observability.app.launchdarkly.com"));
                Assert.That(config.ServiceName, Is.EqualTo(string.Empty));
                Assert.That(config.ServiceVersion, Is.EqualTo(string.Empty));
                Assert.That(config.Environment, Is.EqualTo(string.Empty));
                Assert.That(config.SdkKey, Is.EqualTo("sdk-xyz"));
            });
        }

        [Test]
        public void WithMethods_HandleNullValues_ResetsToDefaults()
        {
            var config = ObservabilityConfig.Builder()
                .WithOtlpEndpoint(null)
                .WithBackendUrl(null)
                .WithServiceName(null)
                .WithServiceVersion(null)
                .WithEnvironment(null)
                .Build("my-sdk-key");

            Assert.Multiple(() =>
            {
                Assert.That(config.OtlpEndpoint, Is.EqualTo("https://otel.observability.app.launchdarkly.com:4318"));
                Assert.That(config.BackendUrl, Is.EqualTo("https://pub.observability.app.launchdarkly.com"));
                Assert.That(config.ServiceName, Is.EqualTo(string.Empty));
                Assert.That(config.ServiceVersion, Is.EqualTo(string.Empty));
                Assert.That(config.Environment, Is.EqualTo(string.Empty));
                Assert.That(config.SdkKey, Is.EqualTo("my-sdk-key"));
            });
        }

        [Test]
        public void Build_ProducesImmutableConfig()
        {
            var builder = ObservabilityConfig.Builder()
                .WithOtlpEndpoint("e1")
                .WithBackendUrl("b1")
                .WithServiceName("s1")
                .WithServiceVersion("v1")
                .WithEnvironment("env1");

            var first = builder.Build("my-sdk-key");

            // Change builder afterward
            builder
                .WithOtlpEndpoint("e2")
                .WithBackendUrl("b2")
                .WithServiceName("s2")
                .WithServiceVersion("v2")
                .WithEnvironment("env2");

            Assert.Multiple(() =>
            {
                // Previously built config remains unchanged
                Assert.That(first.OtlpEndpoint, Is.EqualTo("e1"));
                Assert.That(first.BackendUrl, Is.EqualTo("b1"));
                Assert.That(first.ServiceName, Is.EqualTo("s1"));
                Assert.That(first.ServiceVersion, Is.EqualTo("v1"));
                Assert.That(first.Environment, Is.EqualTo("env1"));
                Assert.That(first.SdkKey, Is.EqualTo("my-sdk-key"));
            });
        }

        [Test]
        public void WithExtendedTracingConfig_StoresConfigurationAction()
        {
            var wasCalled = false;
            TracerProviderBuilder capturedBuilder = null;

            Action<TracerProviderBuilder> customAction = builder =>
            {
                wasCalled = true;
                capturedBuilder = builder;
            };

            var config = ObservabilityConfig.Builder()
                .WithExtendedTracingConfig(customAction)
                .Build("sdk-key");

            // The action is stored but not executed during build
            Assert.That(config.ExtendedTracerConfiguration, Is.Not.Null);
            Assert.That(config.ExtendedTracerConfiguration, Is.SameAs(customAction));

            // Verify the action hasn't been called yet
            Assert.That(wasCalled, Is.False);
            Assert.That(capturedBuilder, Is.Null);
        }

        [Test]
        public void WithExtendedLoggerConfiguration_StoresConfigurationAction()
        {
            var wasCalled = false;
            LoggerProviderBuilder capturedBuilder = null;

            Action<LoggerProviderBuilder> customAction = builder =>
            {
                wasCalled = true;
                capturedBuilder = builder;
            };

            var config = ObservabilityConfig.Builder()
                .WithExtendedLoggerConfiguration(customAction)
                .Build("sdk-key");

            // The action is stored but not executed during build
            Assert.That(config.ExtendedLoggerConfiguration, Is.Not.Null);
            Assert.That(config.ExtendedLoggerConfiguration, Is.SameAs(customAction));

            // Verify the action hasn't been called yet
            Assert.That(wasCalled, Is.False);
            Assert.That(capturedBuilder, Is.Null);
        }

        [Test]
        public void WithExtendedMeterConfiguration_StoresConfigurationAction()
        {
            var wasCalled = false;
            MeterProviderBuilder capturedBuilder = null;

            Action<MeterProviderBuilder> customAction = builder =>
            {
                wasCalled = true;
                capturedBuilder = builder;
            };

            var config = ObservabilityConfig.Builder()
                .WithExtendedMeterConfiguration(customAction)
                .Build("sdk-key");

            // The action is stored but not executed during build
            Assert.That(config.ExtendedMeterConfiguration, Is.Not.Null);
            Assert.That(config.ExtendedMeterConfiguration, Is.SameAs(customAction));

            // Verify the action hasn't been called yet
            Assert.That(wasCalled, Is.False);
            Assert.That(capturedBuilder, Is.Null);
        }

        [Test]
        public void WithExtendedTracingConfig_CanChainMultipleCalls()
        {
            Action<TracerProviderBuilder> secondAction = _ => { };

            // The second call should replace the first
            var config = ObservabilityConfig.Builder()
                .WithExtendedTracingConfig(_ => { })
                .WithExtendedTracingConfig(secondAction)
                .Build("sdk-key");

            Assert.That(config.ExtendedTracerConfiguration, Is.SameAs(secondAction));
        }

        [Test]
        public void WithExtendedLoggerConfiguration_CanChainMultipleCalls()
        {
            Action<LoggerProviderBuilder> secondAction = _ => { };

            // The second call should replace the first
            var config = ObservabilityConfig.Builder()
                .WithExtendedLoggerConfiguration(_ => { })
                .WithExtendedLoggerConfiguration(secondAction)
                .Build("sdk-key");

            Assert.That(config.ExtendedLoggerConfiguration, Is.SameAs(secondAction));
        }

        [Test]
        public void WithExtendedMeterConfiguration_CanChainMultipleCalls()
        {
            Action<MeterProviderBuilder> secondAction = _ => { };

            // The second call should replace the first
            var config = ObservabilityConfig.Builder()
                .WithExtendedMeterConfiguration(_ => { })
                .WithExtendedMeterConfiguration(secondAction)
                .Build("sdk-key");

            Assert.That(config.ExtendedMeterConfiguration, Is.SameAs(secondAction));
        }

        [Test]
        public void WithExtendedConfigurations_AllCanBeSetIndependently()
        {
            Action<TracerProviderBuilder> tracerAction = _ => { };
            Action<LoggerProviderBuilder> loggerAction = _ => { };
            Action<MeterProviderBuilder> meterAction = _ => { };

            var config = ObservabilityConfig.Builder()
                .WithExtendedTracingConfig(tracerAction)
                .WithExtendedLoggerConfiguration(loggerAction)
                .WithExtendedMeterConfiguration(meterAction)
                .Build("sdk-key");

            Assert.Multiple(() =>
            {
                Assert.That(config.ExtendedTracerConfiguration, Is.SameAs(tracerAction));
                Assert.That(config.ExtendedLoggerConfiguration, Is.SameAs(loggerAction));
                Assert.That(config.ExtendedMeterConfiguration, Is.SameAs(meterAction));
            });
        }

        [Test]
        public void WithExtendedConfigurations_NullActionsAreAccepted()
        {
            var config = ObservabilityConfig.Builder()
                .WithExtendedTracingConfig(null)
                .WithExtendedLoggerConfiguration(null)
                .WithExtendedMeterConfiguration(null)
                .Build("sdk-key");

            Assert.Multiple(() =>
            {
                Assert.That(config.ExtendedTracerConfiguration, Is.Null);
                Assert.That(config.ExtendedLoggerConfiguration, Is.Null);
                Assert.That(config.ExtendedMeterConfiguration, Is.Null);
            });
        }
    }
}
