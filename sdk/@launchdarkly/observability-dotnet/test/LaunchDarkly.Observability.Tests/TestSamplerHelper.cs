using System.Collections.Generic;
using System.Diagnostics;
using LaunchDarkly.Observability.Sampling;
using OpenTelemetry.Logs;

namespace LaunchDarkly.Observability.Test
{
    /// <summary>
    /// Helper class providing test samplers for unit tests
    /// </summary>
    internal static class TestSamplerHelper
    {
        /// <summary>
        /// Creates a mock sampler that can be configured with specific sampling results for spans and logs
        /// </summary>
        internal static IExportSampler CreateMockSampler(
            Dictionary<string, bool> spanSampleResults = null,
            bool shouldSampleLogs = true,
            Dictionary<string, object> attributesToAdd = null,
            bool enabled = true)
        {
            return new MockSampler(spanSampleResults, shouldSampleLogs, attributesToAdd, enabled);
        }

        /// <summary>
        /// Mock implementation of IExportSampler for testing
        /// </summary>
        private class MockSampler : IExportSampler
        {
            private readonly Dictionary<string, bool> _spanSampleResults;
            private readonly bool _shouldSampleLogs;
            private readonly Dictionary<string, object> _attributesToAdd;
            private readonly bool _enabled;

            public MockSampler(
                Dictionary<string, bool> spanSampleResults,
                bool shouldSampleLogs,
                Dictionary<string, object> attributesToAdd,
                bool enabled)
            {
                _spanSampleResults = spanSampleResults ?? new Dictionary<string, bool>();
                _shouldSampleLogs = shouldSampleLogs;
                _attributesToAdd = attributesToAdd ?? new Dictionary<string, object>();
                _enabled = enabled;
            }

            public void SetConfig(SamplingConfig config)
            {
                // Not needed for tests
            }

            public SamplingResult SampleSpan(Activity span)
            {
                var spanId = span.SpanId.ToString();
                var shouldSample = _spanSampleResults.GetValueOrDefault(spanId, true);

                return new SamplingResult
                {
                    Sample = shouldSample,
                    Attributes = shouldSample ? _attributesToAdd : new Dictionary<string, object>()
                };
            }

            public SamplingResult SampleLog(LogRecord record)
            {
                return new SamplingResult
                {
                    Sample = _shouldSampleLogs,
                    Attributes = _shouldSampleLogs ? _attributesToAdd : new Dictionary<string, object>()
                };
            }

            public bool IsSamplingEnabled()
            {
                return _enabled;
            }
        }
    }
}