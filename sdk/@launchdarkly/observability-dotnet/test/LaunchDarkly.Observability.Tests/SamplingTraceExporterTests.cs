using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using LaunchDarkly.Observability.Sampling;
using NUnit.Framework;
using OpenTelemetry.Logs;

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class SamplingTraceExporterTests
    {
        [Test]
        public void SampleActivities_WhenSamplingDisabled_ShouldReturnAllActivities()
        {
            // Arrange
            var sampler = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>(),
                enabled: false
            );

            var activities = new[]
            {
                TestActivityHelper.CreateTestActivity("span1"),
                TestActivityHelper.CreateTestActivity("span2")
            };

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, sampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(2));
            Assert.That(sampledActivities, Is.EqualTo(activities));
        }

        [Test]
        public void SampleActivities_WhenSpansAreFilteredOut_ShouldReturnOnlySelectedSpans()
        {
            // Arrange
            var activities = new[]
            {
                TestActivityHelper.CreateTestActivity("span1"),
                TestActivityHelper.CreateTestActivity("span2"),
                TestActivityHelper.CreateTestActivity("span3")
            };

            var sampler = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>
                {
                    [activities[0].SpanId.ToString()] = true, // Include
                    [activities[1].SpanId.ToString()] = false, // Exclude
                    [activities[2].SpanId.ToString()] = true // Include
                },
                attributesToAdd: new Dictionary<string, object> { ["test.sampled"] = true }
            );

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, sampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(2));
            Assert.That(sampledActivities.Any(a => a.DisplayName == "span1"), Is.True);
            Assert.That(sampledActivities.Any(a => a.DisplayName == "span3"), Is.True);
            Assert.That(sampledActivities.Any(a => a.DisplayName == "span2"), Is.False);
        }

        [Test]
        public void SampleActivities_WhenParentSpanIsFilteredOut_ShouldAlsoFilterOutChildren()
        {
            // Arrange
            var parentActivity = TestActivityHelper.CreateTestActivity("parent");
            var childActivity = TestActivityHelper.CreateTestActivity("child", parentActivity.SpanId.ToString());
            var independentActivity = TestActivityHelper.CreateTestActivity("independent");

            var activities = new[] { parentActivity, childActivity, independentActivity };

            var sampler = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>
                {
                    [parentActivity.SpanId.ToString()] = false, // Parent filtered out
                    [childActivity.SpanId.ToString()] = true, // Child would be included but parent isn't
                    [independentActivity.SpanId.ToString()] = true // Independent span included
                },
                attributesToAdd: new Dictionary<string, object> { ["test.sampled"] = true }
            );

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, sampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(1));
            Assert.That(sampledActivities[0].DisplayName, Is.EqualTo("independent"));
        }

        [Test]
        public void SampleActivities_WhenNoActivitiesPassSampling_ShouldReturnEmptyList()
        {
            // Arrange
            var activities = new[]
            {
                TestActivityHelper.CreateTestActivity("span1"),
                TestActivityHelper.CreateTestActivity("span2")
            };

            var sampler = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>
                {
                    [activities[0].SpanId.ToString()] = false,
                    [activities[1].SpanId.ToString()] = false
                }
            );

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, sampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(0));
        }

        [Test]
        public void SampleActivities_WhenSamplingAttributesAreAdded_ShouldIncludeThemInActivities()
        {
            // Arrange
            var activities = new[]
            {
                TestActivityHelper.CreateTestActivity("span1")
            };

            var sampler = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>
                {
                    [activities[0].SpanId.ToString()] = true
                },
                attributesToAdd: new Dictionary<string, object> { ["test.sampled"] = true }
            );

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, sampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(1));
            Assert.That(sampledActivities[0].TagObjects.Any(t => t.Key == "test.sampled" && t.Value.Equals(true)),
                Is.True);
        }
    }
}
