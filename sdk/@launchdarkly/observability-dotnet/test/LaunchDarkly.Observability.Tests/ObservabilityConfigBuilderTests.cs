using LaunchDarkly.Observability;
using NUnit.Framework;

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class ObservabilityConfigBuilderTests
    {
        [Test]
        public void Build_WithAllFields_SetsValues()
        {
            var config = ObservabilityConfig.CreateBuilder("sdk-123")
                .WithOtlpEndpoint("https://otlp.example.com")
                .WithBackendUrl("https://backend.example.com")
                .WithServiceName("service-a")
                .WithServiceVersion("1.0.0")
                .WithEnvironment("prod")
                .Build();

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
            var config = ObservabilityConfig.CreateBuilder("sdk-xyz").Build();

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
            var config = ObservabilityConfig.CreateBuilder("sdk-null")
                .WithOtlpEndpoint(null)
                .WithBackendUrl(null)
                .WithServiceName(null)
                .WithServiceVersion(null)
                .WithEnvironment(null)
                .Build();

            Assert.Multiple(() =>
            {
                Assert.That(config.OtlpEndpoint, Is.EqualTo("https://otel.observability.app.launchdarkly.com:4318"));
                Assert.That(config.BackendUrl, Is.EqualTo("https://pub.observability.app.launchdarkly.com"));
                Assert.That(config.ServiceName, Is.EqualTo(string.Empty));
                Assert.That(config.ServiceVersion, Is.EqualTo(string.Empty));
                Assert.That(config.Environment, Is.EqualTo(string.Empty));
                Assert.That(config.SdkKey, Is.EqualTo("sdk-null"));
            });
        }

        [Test]
        public void Build_ProducesImmutableConfig()
        {
            var builder = ObservabilityConfig.CreateBuilder("sdk-immutable")
                .WithOtlpEndpoint("e1")
                .WithBackendUrl("b1")
                .WithServiceName("s1")
                .WithServiceVersion("v1")
                .WithEnvironment("env1");

            var first = builder.Build();

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
                Assert.That(first.SdkKey, Is.EqualTo("sdk-immutable"));
            });
        }
    }
}
