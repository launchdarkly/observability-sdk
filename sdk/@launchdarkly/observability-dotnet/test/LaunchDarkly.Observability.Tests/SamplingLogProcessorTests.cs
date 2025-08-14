using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using LaunchDarkly.Observability.Otel;
using LaunchDarkly.Observability.Sampling;
using Microsoft.Extensions.Logging;
using NUnit.Framework;
using OpenTelemetry.Logs;

namespace LaunchDarkly.Observability.Test
{
    [TestFixture]
    public class SamplingLogProcessorTests
    {
        #region Tests

        [Test]
        public void OnEnd_WhenSamplerReturnsFalse_ShouldNotModifyAttributes()
        {
            var sampler = TestSamplerHelper.CreateMockSampler(shouldSampleLogs: false);
            var processor = new SamplingLogProcessor(sampler);
            var logRecord = LogRecordHelper.CreateTestLogRecord(LogLevel.Information, "Test message");
            var originalAttributes = logRecord.Attributes?.ToList();

            processor.OnEnd(logRecord);

            var finalAttributes = logRecord.Attributes?.ToList();

            if (originalAttributes == null)
            {
                Assert.That(finalAttributes, Is.Null.Or.Empty);
            }
            else
            {
                Assert.That(finalAttributes?.Count, Is.EqualTo(originalAttributes.Count));
            }
        }

        [Test]
        public void OnEnd_WhenSamplerReturnsTrue_WithNoAttributes_ShouldNotModifyRecord()
        {
            var sampler = TestSamplerHelper.CreateMockSampler(
                shouldSampleLogs: true,
                attributesToAdd: new Dictionary<string, object>()
            );
            var processor = new SamplingLogProcessor(sampler);
            var logRecord = LogRecordHelper.CreateTestLogRecord(LogLevel.Information, "Test message");
            var originalAttributes = logRecord.Attributes?.ToList();

            processor.OnEnd(logRecord);

            var finalAttributes = logRecord.Attributes?.ToList();

            if (originalAttributes == null)
            {
                Assert.That(finalAttributes, Is.Null.Or.Empty);
            }
            else
            {
                Assert.That(finalAttributes?.Count, Is.EqualTo(originalAttributes.Count));
            }
        }

        [Test]
        public void OnEnd_WhenSamplerAddsAttributes_ShouldMergeWithExistingAttributes()
        {
            var samplingAttributes = new Dictionary<string, object>
            {
                ["sampling.ratio"] = 0.5,
                ["sampler.type"] = "custom"
            };
            var sampler = TestSamplerHelper.CreateMockSampler(
                shouldSampleLogs: true,
                attributesToAdd: samplingAttributes
            );
            var processor = new SamplingLogProcessor(sampler);

            var existingAttributes = new Dictionary<string, object>
            {
                ["existing.key"] = "existing.value",
                ["another.key"] = 42
            };
            var logRecord = LogRecordHelper.CreateTestLogRecord(LogLevel.Information, "Test message with attributes",
                existingAttributes);

            processor.OnEnd(logRecord);

            Assert.That(logRecord.Attributes, Is.Not.Null);

            var attributesList = logRecord.Attributes.ToList();

            Assert.Multiple(() =>
            {
                // Check that sampling attributes were added
                Assert.That(attributesList.Any(kvp =>
                {
                    Assert.That(kvp.Value, Is.Not.Null);
                    return kvp.Key == "sampling.ratio" && kvp.Value.Equals(0.5);
                }), Is.True);
                Assert.That(attributesList.Any(kvp =>
                {
                    Assert.That(kvp.Value, Is.Not.Null);
                    return kvp.Key == "sampler.type" && kvp.Value.Equals("custom");
                }), Is.True);

                // Check that existing attributes are preserved
                Assert.That(attributesList.Any(kvp =>
                    {
                        Assert.That(kvp.Value, Is.Not.Null);
                        return kvp.Key == "existing.key" && kvp.Value.Equals("existing.value");
                    }),
                    Is.True);
                Assert.That(attributesList.Any(kvp =>
                {
                    Assert.That(kvp.Value, Is.Not.Null);
                    return kvp.Key == "another.key" && kvp.Value.Equals(42);
                }), Is.True);
            });
        }

        [Test]
        public void OnEnd_WhenLogHasNoExistingAttributes_ShouldAddSamplingAttributes()
        {
            var samplingAttributes = new Dictionary<string, object>
            {
                ["sampling.ratio"] = 1.0,
                ["sampler.enabled"] = true
            };
            var sampler = TestSamplerHelper.CreateMockSampler(
                shouldSampleLogs: true,
                attributesToAdd: samplingAttributes
            );
            var processor = new SamplingLogProcessor(sampler);
            var logRecord =
                LogRecordHelper.CreateTestLogRecord(LogLevel.Information, "Test message without attributes");

            processor.OnEnd(logRecord);

            Assert.That(logRecord.Attributes, Is.Not.Null);

            var attributesList = logRecord.Attributes.ToList();

            Assert.Multiple(() =>
            {
                // Check that sampling attributes were added
                Assert.That(attributesList.Any(kvp =>
                {
                    Assert.That(kvp.Value, Is.Not.Null);
                    return kvp.Key == "sampling.ratio" && kvp.Value.Equals(1.0);
                }), Is.True);
                Assert.That(attributesList.Any(kvp =>
                {
                    Assert.That(kvp.Value, Is.Not.Null);
                    return kvp.Key == "sampler.enabled" && kvp.Value.Equals(true);
                }), Is.True);
            });
        }

        [Test]
        public void OnEnd_WhenSamplingRatioAttributeIsAdded_ShouldBeIncludedInLogAttributes()
        {
            var samplingAttributes = new Dictionary<string, object>
            {
                ["sampling.ratio"] = 0.25
            };
            var sampler = TestSamplerHelper.CreateMockSampler(
                shouldSampleLogs: true,
                attributesToAdd: samplingAttributes
            );
            var processor = new SamplingLogProcessor(sampler);
            var logRecord =
                LogRecordHelper.CreateTestLogRecord(LogLevel.Information, "Test message for sampling ratio");

            processor.OnEnd(logRecord);

            Assert.That(logRecord.Attributes, Is.Not.Null);

            var attributesList = logRecord.Attributes.ToList();
            Assert.That(attributesList.Any(kvp =>
            {
                Assert.That(kvp.Value, Is.Not.Null);
                return kvp.Key == "sampling.ratio" && kvp.Value.Equals(0.25);
            }), Is.True);
        }

        [Test]
        public void OnEnd_WhenSamplerHasEmptyAttributes_ShouldNotAddAttributes()
        {
            var sampler = TestSamplerHelper.CreateMockSampler(
                shouldSampleLogs: true,
                attributesToAdd: new Dictionary<string, object>()
            );
            var processor = new SamplingLogProcessor(sampler);

            var existingAttributes = new Dictionary<string, object>
            {
                ["original.key"] = "original.value"
            };
            var logRecord =
                LogRecordHelper.CreateTestLogRecord(LogLevel.Information, "Test message", existingAttributes);
            var originalAttributesList = logRecord.Attributes?.ToList() ?? new List<KeyValuePair<string, object>>();

            processor.OnEnd(logRecord);

            var finalAttributesList = logRecord.Attributes?.ToList() ?? new List<KeyValuePair<string, object>>();
            Assert.That(finalAttributesList, Has.Count.EqualTo(originalAttributesList.Count));
            Assert.That(finalAttributesList.Any(kvp =>
                {
                    Assert.That(kvp.Value, Is.Not.Null);
                    return kvp.Key == "original.key" && kvp.Value.Equals("original.value");
                }),
                Is.True);
        }

        #endregion
    }
}
