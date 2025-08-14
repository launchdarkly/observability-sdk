using System;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class ObservabilityPluginBuilderTests
    {
        [SetUp]
        public void SetUp()
        {
            _services = new ServiceCollection();
        }

        private IServiceCollection _services;

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
    }
}
