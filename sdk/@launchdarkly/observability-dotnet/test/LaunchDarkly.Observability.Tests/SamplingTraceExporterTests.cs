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
        #region Helper Classes

        /// <summary>
        /// Mock sampler for testing
        /// </summary>
        private class MockSampler : IExportSampler
        {
            private readonly Dictionary<string, bool> _sampleResults;
            private readonly bool _enabled;

            public MockSampler(Dictionary<string, bool> sampleResults, bool enabled = true)
            {
                _sampleResults = sampleResults;
                _enabled = enabled;
            }

            public void SetConfig(SamplingConfig config) { }

            public SamplingResult SampleSpan(Activity span)
            {
                var spanId = span.SpanId.ToString();
                var shouldSample = _sampleResults.ContainsKey(spanId) ? _sampleResults[spanId] : true;

                return new SamplingResult
                {
                    Sample = shouldSample,
                    Attributes = shouldSample ? new Dictionary<string, object> { ["test.sampled"] = true } : new Dictionary<string, object>()
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
        /// Helper to create test activities
        /// </summary>
        private static Activity CreateTestActivity(string name, string parentSpanId = null)
        {
            var activity = new Activity(name);
            activity.Start();
            activity.DisplayName = name;

            if (!string.IsNullOrEmpty(parentSpanId))
            {
                var traceId = ActivityTraceId.CreateRandom();
                var parentSpan = ActivitySpanId.CreateFromString(parentSpanId.PadRight(16, '0'));
                var parentContext = new ActivityContext(traceId, parentSpan, ActivityTraceFlags.Recorded);
                
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
        public void SampleActivities_WhenSamplingDisabled_ShouldReturnAllActivities()
        {
            // Arrange
            var sampler = new MockSampler(new Dictionary<string, bool>(), enabled: false);

            var activities = new[]
            {
                CreateTestActivity("span1"),
                CreateTestActivity("span2")
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
                CreateTestActivity("span1"),
                CreateTestActivity("span2"),
                CreateTestActivity("span3")
            };

            var sampler = new MockSampler(new Dictionary<string, bool>
            {
                [activities[0].SpanId.ToString()] = true,  // Include
                [activities[1].SpanId.ToString()] = false, // Exclude
                [activities[2].SpanId.ToString()] = true   // Include
            });

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
            var parentActivity = CreateTestActivity("parent");
            var childActivity = CreateTestActivity("child", parentActivity.SpanId.ToString());
            var independentActivity = CreateTestActivity("independent");

            var activities = new[] { parentActivity, childActivity, independentActivity };

            var sampler = new MockSampler(new Dictionary<string, bool>
            {
                [parentActivity.SpanId.ToString()] = false,      // Parent filtered out
                [childActivity.SpanId.ToString()] = true,        // Child would be included but parent isn't
                [independentActivity.SpanId.ToString()] = true   // Independent span included
            });

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
                CreateTestActivity("span1"),
                CreateTestActivity("span2")
            };

            var sampler = new MockSampler(new Dictionary<string, bool>
            {
                [activities[0].SpanId.ToString()] = false,
                [activities[1].SpanId.ToString()] = false
            });

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
                CreateTestActivity("span1")
            };

            var sampler = new MockSampler(new Dictionary<string, bool>
            {
                [activities[0].SpanId.ToString()] = true
            });

            // Act
            var sampledActivities = SampleSpans.SampleActivities(activities, sampler);

            // Assert
            Assert.That(sampledActivities.Count, Is.EqualTo(1));
            Assert.That(sampledActivities[0].TagObjects.Any(t => t.Key == "test.sampled" && t.Value.Equals(true)), Is.True);
        }
    }
}