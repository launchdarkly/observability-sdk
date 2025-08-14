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
        #region Helper Classes and Methods

        /// <summary>
        /// Mock implementation of IExportSampler for testing
        /// </summary>
        private class MockSampler : IExportSampler
        {
            private readonly Dictionary<string, bool> _mockResults;
            private readonly bool _enabled;

            public MockSampler(Dictionary<string, bool> mockResults, bool enabled = true)
            {
                _mockResults = mockResults;
                _enabled = enabled;
            }

            public void SetConfig(SamplingConfig config)
            {
                // Not needed for tests
            }

            public SamplingResult SampleSpan(Activity span)
            {
                var spanId = span.SpanId.ToString();
                var shouldSample = _mockResults.GetValueOrDefault(spanId, true);

                return new SamplingResult
                {
                    Sample = shouldSample,
                    Attributes = shouldSample
                        ? new Dictionary<string, object> { ["samplingRatio"] = 2 }
                        : new Dictionary<string, object>()
                };
            }

            public SamplingResult SampleLog(LogRecord record)
            {
                return new SamplingResult { Sample = true };
            }

            public bool IsSamplingEnabled()
            {
                return _enabled;
            }
        }

        /// <summary>
        /// Helper method to create a mock Activity
        /// </summary>
        private static Activity CreateMockActivity(string name, string parentSpanId = null)
        {
            var activity = new Activity(name);
            activity.Start();
            activity.DisplayName = name;

            // Set a custom span ID for predictable testing
            activity.SetIdFormat(ActivityIdFormat.W3C);

            // If we have a parent span ID, we need to create a proper parent context
            if (!string.IsNullOrEmpty(parentSpanId))
            {
                // Create a trace ID and parent span context
                var traceId = ActivityTraceId.CreateRandom();
                var parentSpan = ActivitySpanId.CreateFromString(parentSpanId.PadRight(16, '0'));
                var parentContext = new ActivityContext(traceId, parentSpan, ActivityTraceFlags.Recorded);

                // Stop and recreate the activity with the parent context
                activity.Stop();
                activity = new Activity(name);
                activity.SetParentId(parentContext.TraceId, parentContext.SpanId, parentContext.TraceFlags);
                activity.Start();
                activity.DisplayName = name;
            }

            activity.Stop();
            return activity;
        }

        #endregion

        [Test]
        public void SampleActivities_ShouldRemoveSpansThatAreNotSampled()
        {
            var activities = new List<Activity>
            {
                CreateMockActivity("span-1"), // Root span - sampled
                CreateMockActivity("span-2") // Root span - not sampled
            };

            var span1 = activities[0];
            var span2 = activities[1];

            // We need to mock the span IDs in our sampler
            var samplerWithRealIds = new MockSampler(new Dictionary<string, bool>
            {
                [span1.SpanId.ToString()] = true,
                [span2.SpanId.ToString()] = false
            });

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
            var parentActivity = CreateMockActivity("parent");
            var childActivity = CreateMockActivity("child", parentActivity.SpanId.ToString());
            var grandchildActivity = CreateMockActivity("grandchild", childActivity.SpanId.ToString());
            var rootActivity = CreateMockActivity("root");

            var activities = new List<Activity>
            {
                parentActivity,
                childActivity,
                grandchildActivity,
                rootActivity
            };

            var mockSampler = new MockSampler(new Dictionary<string, bool>
            {
                [parentActivity.SpanId.ToString()] = false, // Parent not sampled
                [childActivity.SpanId.ToString()] = true, // Child would be sampled but parent isn't
                [grandchildActivity.SpanId.ToString()] = true, // Grandchild would be sampled but parent isn't
                [rootActivity.SpanId.ToString()] = true // Root sampled
            });

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
            var mockSampler = new MockSampler(
                new Dictionary<string, bool>(), // Empty results
                enabled: false // Sampling disabled
            );

            var activities = new List<Activity>
            {
                CreateMockActivity("span-1"),
                CreateMockActivity("span-2")
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
                CreateMockActivity("span-1"),
                CreateMockActivity("span-2")
            };

            var mockSampler = new MockSampler(new Dictionary<string, bool>
            {
                [activities[0].SpanId.ToString()] = true,
                [activities[1].SpanId.ToString()] = true
            });

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
