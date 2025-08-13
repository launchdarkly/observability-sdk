using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using LaunchDarkly.Observability.Sampling;
using NUnit.Framework;
using OpenTelemetry.Logs;

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class CustomSamplerTests
    {
        #region Test Models

        public class SpanTestScenario
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

        public class LogTestScenario
        {
            public string Description { get; set; }
            public SamplingConfig SamplingConfig { get; set; }
            public InputLog InputLog { get; set; }
            public SamplerFunctionCase[] SamplerFunctionCases { get; set; }
        }

        public class InputSpan
        {
            public string Name { get; set; }
            public Dictionary<string, JsonElement> Attributes { get; set; } = new Dictionary<string, JsonElement>();
            public InputEvent[] Events { get; set; } = new InputEvent[0];
        }

        public class InputLog
        {
            public string Message { get; set; }
            public Dictionary<string, object> Attributes { get; set; } = new Dictionary<string, object>();
            public string SeverityText { get; set; }
        }

        public class InputEvent
        {
            public string Name { get; set; }
            public Dictionary<string, JsonElement> Attributes { get; set; } = new Dictionary<string, JsonElement>();
        }

        public class SamplerFunctionCase
        {
            public string Type { get; set; } // "always" or "never"
            public ExpectedResult Expected_result { get; set; }
        }

        public class ExpectedResult
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

        private static Activity CreateMockActivity(InputSpan inputSpan)
        {
            var activity = new Activity("test-operation");
            activity.Start(); // Start the activity properly
            activity.DisplayName = inputSpan.Name;

            // Add attributes as tags
            if (inputSpan.Attributes != null)
            {
                foreach (var attr in inputSpan.Attributes)
                {
                    activity.SetTag(attr.Key, getJsonRawValue(attr));
                }
            }

            // Add events
            if (inputSpan.Events != null)
            {
                foreach (var evt in inputSpan.Events)
                {
                    var tags = evt.Attributes?.Select(kvp => new KeyValuePair<string, object>(kvp.Key, getJsonRawValue(kvp)));
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

        private static object getJsonRawValue(KeyValuePair<string, JsonElement> attr)
        {
            object value = null;
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

        // TODO: Implement proper LogRecord creation
        // For now, we'll skip log tests to focus on span tests first

        private static T LoadJsonTestData<T>(string fileName)
        {
            var testDir = TestContext.CurrentContext.TestDirectory;
            var filePath = Path.Combine(testDir, fileName);

            if (!File.Exists(filePath))
            {
                // Try relative path from current working directory
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

        #endregion

        #region Span Tests

        private static SpanTestScenario[] GetSpanTestScenarios()
        {
            return LoadJsonTestData<SpanTestScenario[]>("span-test-scenarios.json");
        }

        [Test, TestCaseSource(nameof(GetSpanTestScenarios))]
        public void SpanSamplingTests(SpanTestScenario scenario)
        {
            var scenarios = LoadJsonTestData<SpanTestScenario[]>("span-test-scenarios.json");


            foreach (var samplerCase in scenario.SamplerFunctionCases)
            {
                var samplerFn = SamplerFunctions[samplerCase.Type];
                var testName = $"{scenario.Description} - {samplerCase.Type}";

                TestContext.WriteLine($"Running test: {testName}");

                var sampler = new CustomSampler(samplerFn);
                sampler.SetConfig(scenario.SamplingConfig);

                Assert.That(sampler.IsSamplingEnabled(), Is.True, $"Sampling should be enabled for: {testName}");

                // Debug: Print config details
                TestContext.WriteLine($"Config has {scenario.SamplingConfig.Spans?.Count ?? 0} span configs");
                if (scenario.SamplingConfig.Spans?.Count > 0)
                {
                    var spanConfig = scenario.SamplingConfig.Spans[0];
                    TestContext.WriteLine(
                        $"First span config - Name: {spanConfig.Name?.MatchValue}, Ratio: {spanConfig.SamplingRatio}");
                }

                using (var activity = CreateMockActivity(scenario.InputSpan))
                {
                    // Debug: Print activity details
                    TestContext.WriteLine(
                        $"Activity DisplayName: '{activity.DisplayName}', OperationName: '{activity.OperationName}'");
                    TestContext.WriteLine($"Input span name: '{scenario.InputSpan.Name}'");

                    // Check if the activity has been started
                    TestContext.WriteLine($"Activity HasStarted: {activity.Id != null}, Id: {activity.Id}");

                    var result = sampler.SampleSpan(activity);

                    // Debug: Print result details
                    TestContext.WriteLine(
                        $"Result - Sample: {result.Sample}, Attributes: {(result.Attributes != null ? string.Join(", ", result.Attributes.Select(kv => $"{kv.Key}={kv.Value}")) : "null")}");

                    Assert.That(result.Sample, Is.EqualTo(samplerCase.Expected_result.Sample),
                        $"Sample result mismatch for: {testName}");

                    if (samplerCase.Expected_result.Attributes != null)
                    {
                        Assert.That(result.Attributes, Is.Not.Null, $"Attributes should not be null for: {testName}");

                        foreach (var expectedAttr in samplerCase.Expected_result.Attributes)
                        {
                            Assert.That(result.Attributes.ContainsKey(expectedAttr.Key), Is.True,
                                $"Missing attribute '{expectedAttr.Key}' for: {testName}");
                            var actualValue = result.Attributes[expectedAttr.Key];
                            var expectedValue = expectedAttr.Value;

                            // Handle JsonElement comparison for numeric values
                            if (expectedValue is JsonElement jsonElement &&
                                jsonElement.ValueKind == JsonValueKind.Number)
                            {
                                if (jsonElement.TryGetInt32(out var intValue))
                                {
                                    Assert.That(actualValue, Is.EqualTo(intValue),
                                        $"Attribute value mismatch for '{expectedAttr.Key}' in: {testName}");
                                }
                                else
                                {
                                    Assert.That(actualValue, Is.EqualTo(jsonElement.GetDouble()),
                                        $"Attribute value mismatch for '{expectedAttr.Key}' in: {testName}");
                                }
                            }
                            else
                            {
                                Assert.That(actualValue, Is.EqualTo(expectedValue),
                                    $"Attribute value mismatch for '{expectedAttr.Key}' in: {testName}");
                            }
                        }
                    }
                    else
                    {
                        Assert.That(result.Attributes, Is.Null.Or.Empty,
                            $"Attributes should be null/empty for: {testName}");
                    }
                }
            }
        }

        #endregion

        #region Log Tests

        [Test]
        public void LogSamplingTests()
        {
            var scenarios = LoadJsonTestData<LogTestScenario[]>("log-test-scenarios.json");

            foreach (var scenario in scenarios)
            {
                foreach (var samplerCase in scenario.SamplerFunctionCases)
                {
                    var samplerFn = SamplerFunctions[samplerCase.Type];
                    var testName = $"{scenario.Description} - {samplerCase.Type}";

                    TestContext.WriteLine($"Running test: {testName}");

                    var sampler = new CustomSampler(samplerFn);
                    sampler.SetConfig(scenario.SamplingConfig);

                    Assert.That(sampler.IsSamplingEnabled(), Is.True, $"Sampling should be enabled for: {testName}");
                    
                    
                    // TODO: Implement LogRecord creation - for now skip log tests
                    TestContext.WriteLine(
                        $"Skipping log test: {testName} - LogRecord creation needs proper implementation");
                    Assert.Ignore("LogRecord creation not yet implemented");
                }
            }
        }

        #endregion

        #region Additional Tests (from Node.js implementation)

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

        #endregion
    }
}
