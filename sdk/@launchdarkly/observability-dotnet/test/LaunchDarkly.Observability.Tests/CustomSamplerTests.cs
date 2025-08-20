using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using LaunchDarkly.Observability.Sampling;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using NUnit.Framework;
using OpenTelemetry.Logs;

// The test scenario types are used for JSON serialization and as a result code analysis may not realize that they
// are constructed and that their fields are assigned.

// Many things in the tests are public, which may not appear to need to be public, because they need to be consistent
// with the test function access level. So it test functions are public (so they can be ran), then their input
// types must be public.

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class CustomSamplerTests
    {
        #region Test Models

        internal class SpanTestScenario
        {
            public string Description { get; set; }
            public SamplingConfig SamplingConfig { get; set; }
            public InputSpan InputSpan { get; set; }
            public SamplerFunctionCase[] SamplerFunctionCases { get; set; }

            public override string ToString()
            {
                return Description;
            }
        }

        internal class LogTestScenario
        {
            public string Description { get; set; }
            public SamplingConfig SamplingConfig { get; set; }
            public InputLog InputLog { get; set; }
            public SamplerFunctionCase[] SamplerFunctionCases { get; set; }

            public override string ToString()
            {
                return Description;
            }
        }

        internal class InputSpan
        {
            public string Name { get; set; }
            public Dictionary<string, JsonElement> Attributes { get; set; } = new Dictionary<string, JsonElement>();
            public InputEvent[] Events { get; set; } = new InputEvent[0];
        }

        internal class InputLog
        {
            public string Message { get; set; }
            public Dictionary<string, JsonElement> Attributes { get; set; } = new Dictionary<string, JsonElement>();
            public string SeverityText { get; set; }
        }

        internal class InputEvent
        {
            public string Name { get; set; }
            public Dictionary<string, JsonElement> Attributes { get; set; }
        }

        internal class SamplerFunctionCase
        {
            public string Type { get; set; } // "always" or "never"
            [JsonPropertyName("expected_result")] public ExpectedResult Result { get; set; }
        }

        internal class ExpectedResult
        {
            public bool Sample { get; set; }
            public Dictionary<string, object> Attributes { get; set; }
        }

        #endregion

        #region Test Helper Methods

        private static readonly SamplerFunc AlwaysSampleFn = ratio => true;
        private static readonly SamplerFunc NeverSampleFn = ratio => false;

        private static readonly Dictionary<string, SamplerFunc> SamplerFunctions = new Dictionary<string, SamplerFunc>
        {
            { "always", AlwaysSampleFn },
            { "never", NeverSampleFn }
        };

        private static Activity CreateActivity(InputSpan inputSpan)
        {
            var activity = new Activity("test-operation");
            activity.Start(); // Start the activity properly
            activity.DisplayName = inputSpan.Name;

            // Add attributes as tags
            if (inputSpan.Attributes != null)
            {
                foreach (var attr in inputSpan.Attributes)
                {
                    activity.SetTag(attr.Key, GetJsonRawValue(attr));
                }
            }

            // Add events
            if (inputSpan.Events != null)
            {
                foreach (var evt in inputSpan.Events)
                {
                    var tags = evt.Attributes?.Select(kvp =>
                        new KeyValuePair<string, object>(kvp.Key, GetJsonRawValue(kvp)));
                    if (tags != null)
                    {
                        activity.AddEvent(new ActivityEvent(evt.Name, DateTimeOffset.UtcNow,
                            new ActivityTagsCollection(tags)));
                    }
                    else
                    {
                        activity.AddEvent(new ActivityEvent(evt.Name));
                    }
                }
            }

            activity.Stop();

            return activity;
        }

        private static object GetJsonRawValue(KeyValuePair<string, JsonElement> attr)
        {
            object value;
            switch (attr.Value.ValueKind)
            {
                case JsonValueKind.Null:
                case JsonValueKind.Undefined:
                case JsonValueKind.Object:
                case JsonValueKind.Array:
                    throw new ArgumentException("Test does not support complex JSON types");
                case JsonValueKind.String:
                    value = attr.Value.GetString();
                    break;
                case JsonValueKind.Number:
                    value = attr.Value.GetDouble();
                    break;
                case JsonValueKind.True:
                case JsonValueKind.False:
                    value = attr.Value.GetBoolean();
                    break;
                default:
                    throw new ArgumentOutOfRangeException();
            }

            return value;
        }

        private static T LoadJsonTestData<T>(string fileName)
        {
            var testDir = TestContext.CurrentContext.TestDirectory;
            var filePath = Path.Combine(testDir, fileName);

            if (!File.Exists(filePath))
            {
                filePath = Path.Combine(".", "test", "LaunchDarkly.Observability.Tests", fileName);
            }

            if (!File.Exists(filePath))
            {
                throw new FileNotFoundException($"Test data file not found: {fileName}");
            }

            var json = File.ReadAllText(filePath);
            var options = new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
                PropertyNameCaseInsensitive = true
            };

            return JsonSerializer.Deserialize<T>(json, options);
        }

        private static void VerifySamplingResult(SamplerFunctionCase samplerCase, SamplingResult result)
        {
            if (samplerCase.Result.Attributes != null)
            {
                Assert.That(result.Attributes, Is.Not.Null);

                foreach (var expectedAttr in samplerCase.Result.Attributes)
                {
                    Assert.That(result.Attributes.ContainsKey(expectedAttr.Key), Is.True);
                    var actualValue = result.Attributes[expectedAttr.Key];
                    var expectedValue = expectedAttr.Value;

                    // Handle JsonElement comparison for numeric values
                    if (expectedValue is JsonElement jsonElement &&
                        jsonElement.ValueKind == JsonValueKind.Number)
                    {
                        if (jsonElement.TryGetInt32(out var intValue))
                        {
                            Assert.That(actualValue, Is.EqualTo(intValue));
                        }
                        else
                        {
                            Assert.That(actualValue, Is.EqualTo(jsonElement.GetDouble()));
                        }
                    }
                    else
                    {
                        Assert.That(actualValue, Is.EqualTo(expectedValue));
                    }
                }
            }
            else
            {
                Assert.That(result.Attributes, Is.Null.Or.Empty);
            }
        }

        private static LogTestScenario[] GetLogTestScenarios()
        {
            return LoadJsonTestData<LogTestScenario[]>("log-test-scenarios.json");
        }

        private LogLevel SeverityTextToLogLevel(string severityText)
        {
            switch (severityText)
            {
                case "Error":
                    return LogLevel.Error;
                case "Warning":
                    return LogLevel.Warning;
                case "Info":
                case null:
                    return LogLevel.Information;
                default:
                    // Unsupported in this test suite.
                    throw new ArgumentOutOfRangeException(nameof(severityText), severityText, null);
            }
        }

        #endregion

        #region Span Tests

        private static SpanTestScenario[] GetSpanTestScenarios()
        {
            return LoadJsonTestData<SpanTestScenario[]>("span-test-scenarios.json");
        }

        [Test, TestCaseSource(nameof(GetSpanTestScenarios))]
        public void SpanSamplingTests(object oScenario)
        {
            // The sampling tests take just an object as a workaround for visibility. We want our config types to
            // be internal. The NUnit test functions must be public, so they cannot use an internal object as an input.
            // So we use an object and cast to the correct type.
            var scenario = (SpanTestScenario)oScenario;

            foreach (var samplerCase in scenario.SamplerFunctionCases)
            {
                var samplerFn = SamplerFunctions[samplerCase.Type];

                var sampler = new CustomSampler(samplerFn);
                sampler.SetConfig(scenario.SamplingConfig);

                Assert.That(sampler.IsSamplingEnabled(), Is.True);

                using (var activity = CreateActivity(scenario.InputSpan))
                {
                    var result = sampler.SampleSpan(activity);

                    Assert.That(result.Sample, Is.EqualTo(samplerCase.Result.Sample));

                    VerifySamplingResult(samplerCase, result);
                }
            }
        }

        #endregion

        #region Log Tests

        [Test, TestCaseSource(nameof(GetLogTestScenarios))]
        public void LogSamplingTests(object oScenario)
        {
            // The sampling tests take just an object as a workaround for visibility. We want our config types to
            // be internal. The NUnit test functions must be public, so they cannot use an internal object as an input.
            // So we use an object and cast to the correct type.
            var scenario = (LogTestScenario)oScenario;
            foreach (var samplerCase in scenario.SamplerFunctionCases)
            {
                var samplerFn = SamplerFunctions[samplerCase.Type];

                var sampler = new CustomSampler(samplerFn);
                sampler.SetConfig(scenario.SamplingConfig);

                Assert.That(sampler.IsSamplingEnabled(), Is.True);

                var properties = new Dictionary<string, object>();
                foreach (var inputLogAttribute in scenario.InputLog.Attributes)
                {
                    properties.Add(inputLogAttribute.Key, GetJsonRawValue(inputLogAttribute));
                }

                var record = LogRecordHelper.CreateTestLogRecord(SeverityTextToLogLevel(scenario.InputLog.SeverityText),
                    scenario.InputLog.Message ?? "", properties);
                Assert.Multiple(() =>
                {
                    // Cursory check that the record is formed properly.
                    Assert.That(scenario.InputLog.Message ?? "", Is.EqualTo(record.Body));
                    Assert.That(scenario.InputLog.Attributes?.Count ?? 0, Is.EqualTo(record.Attributes?.Count ?? 0));
                });

                var res = sampler.SampleLog(record);
                Assert.Multiple(() =>
                {
                    Assert.That(res, Is.Not.Null);
                    Assert.That(res.Sample, Is.EqualTo(samplerCase.Result.Sample));
                });

                VerifySamplingResult(samplerCase, res);
            }
        }

        #endregion

        #region Sampling Method Tests

        [Test]
        public void DefaultSampler_ShouldGetApproximatelyCorrectNumberOfSamples()
        {
            const int samples = 100000;
            var sampled = 0;
            var notSampled = 0;

            for (var i = 0; i < samples; i++)
            {
                var result = DefaultSampler.Sample(2);
                if (result)
                {
                    sampled++;
                }
                else
                {
                    notSampled++;
                }
            }

            var lowerBound = samples / 2 - (samples / 2) * 0.1;
            var upperBound = samples / 2 + (samples / 2) * 0.1;

            Assert.That(sampled, Is.GreaterThan(lowerBound));
            Assert.That(sampled, Is.LessThan(upperBound));
            Assert.That(notSampled, Is.GreaterThan(lowerBound));
            Assert.That(notSampled, Is.LessThan(upperBound));
        }

        [Test]
        public void DefaultSampler_ShouldNotSampleWithRatioZero()
        {
            var result = DefaultSampler.Sample(0);
            Assert.That(result, Is.False);
        }

        [Test]
        public void CustomSampler_ShouldReturnFalseIfSamplingNotEnabled()
        {
            var sampler = new CustomSampler();
            Assert.That(sampler.IsSamplingEnabled(), Is.False);
        }

        [Test]
        public void SampleSpan_WithNonNullConfigContainingNullSpansConfig_ShouldHandleGracefully()
        {
            // Create a non-null config with null Spans property
            var config = new SamplingConfig
            {
                Spans = null, // This should cause a NullReferenceException in the current implementation
                Logs = new List<SamplingConfig.LogSamplingConfig>()
            };

            var sampler = new CustomSampler(AlwaysSampleFn);
            sampler.SetConfig(config);

            // Create a test activity
            var activity = new Activity("test-operation");
            activity.Start();
            activity.DisplayName = "test span";
            activity.Stop();

            // This should fail with the current implementation due to NullReferenceException
            var result = sampler.SampleSpan(activity);
            Assert.That(result.Sample, Is.True); // Expected behavior if properly handled
        }

        [Test]
        public void SampleLog_WithNonNullConfigContainingNullLogsConfig_ShouldHandleGracefully()
        {
            // Create a non-null config with null Logs property
            var config = new SamplingConfig
            {
                Spans = new List<SamplingConfig.SpanSamplingConfig>(),
                Logs = null // This should be handled correctly by the null-conditional operator
            };

            var sampler = new CustomSampler(AlwaysSampleFn);
            sampler.SetConfig(config);

            // Create a test log record
            var properties = new Dictionary<string, object>();
            var record = LogRecordHelper.CreateTestLogRecord(LogLevel.Information, "test message", properties);

            // This should pass as the implementation correctly handles null Logs
            var result = sampler.SampleLog(record);
            Assert.That(result.Sample, Is.True);
        }

        #endregion
    }
}
