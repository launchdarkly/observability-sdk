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
    public class SampleSpansTests
    {
        [Test]
        public void SampleActivities_ShouldRemoveSpansThatAreNotSampled()
        {
            var activities = new List<Activity>
            {
                TestActivityHelper.CreateTestActivity("span-1"), // Root span - sampled
                TestActivityHelper.CreateTestActivity("span-2") // Root span - not sampled
            };

            var span1 = activities[0];
            var span2 = activities[1];

            // We need to mock the span IDs in our sampler
            var samplerWithRealIds = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>
                {
                    [span1.SpanId.ToString()] = true,
                    [span2.SpanId.ToString()] = false
                },
                attributesToAdd: new Dictionary<string, object> { ["samplingRatio"] = 2 }
            );

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, samplerWithRealIds);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(1));
            Assert.That(sampledActivities[0].DisplayName, Is.EqualTo("span-1"));
            Assert.That(sampledActivities[0].TagObjects.Any(t => t.Key == "samplingRatio" && t.Value.Equals(2)),
                Is.True);
        }

        [Test]
        public void SampleActivities_ShouldRemoveChildrenOfSpansThatAreNotSampled()
        {
            // Arrange - Create span hierarchy with parent -> child -> grandchild
            var parentActivity = TestActivityHelper.CreateTestActivity("parent");
            var childActivity = TestActivityHelper.CreateTestActivity("child", parentActivity.SpanId.ToString());
            var grandchildActivity =
                TestActivityHelper.CreateTestActivity("grandchild", childActivity.SpanId.ToString());
            var rootActivity = TestActivityHelper.CreateTestActivity("root");

            var activities = new List<Activity>
            {
                parentActivity,
                childActivity,
                grandchildActivity,
                rootActivity
            };

            var mockSampler = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>
                {
                    [parentActivity.SpanId.ToString()] = false, // Parent not sampled
                    [childActivity.SpanId.ToString()] = true, // Child would be sampled but parent isn't
                    [grandchildActivity.SpanId.ToString()] = true, // Grandchild would be sampled but parent isn't
                    [rootActivity.SpanId.ToString()] = true // Root sampled
                }
            );

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, mockSampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(1));
            Assert.That(sampledActivities[0].DisplayName, Is.EqualTo("root"));
        }

        [Test]
        public void SampleActivities_ShouldNotApplySamplingWhenSamplingIsDisabled()
        {
            // Arrange
            var mockSampler = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>(), // Empty results
                enabled: false // Sampling disabled
            );

            var activities = new List<Activity>
            {
                TestActivityHelper.CreateTestActivity("span-1"),
                TestActivityHelper.CreateTestActivity("span-2")
            };

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, mockSampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(2));
            Assert.That(sampledActivities, Is.EqualTo(activities));
        }

        [Test]
        public void SampleActivities_ShouldApplySamplingAttributesToSampledSpans()
        {
            // Arrange
            var activities = new List<Activity>
            {
                TestActivityHelper.CreateTestActivity("span-1"),
                TestActivityHelper.CreateTestActivity("span-2")
            };

            var mockSampler = TestSamplerHelper.CreateMockSampler(
                spanSampleResults: new Dictionary<string, bool>
                {
                    [activities[0].SpanId.ToString()] = true,
                    [activities[1].SpanId.ToString()] = true
                },
                attributesToAdd: new Dictionary<string, object> { ["samplingRatio"] = 2 }
            );

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, mockSampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(2));
            Assert.That(sampledActivities[0].TagObjects.Any(t => t.Key == "samplingRatio" && t.Value.Equals(2)),
                Is.True);
            Assert.That(sampledActivities[1].TagObjects.Any(t => t.Key == "samplingRatio" && t.Value.Equals(2)),
                Is.True);
        }
    }
}
