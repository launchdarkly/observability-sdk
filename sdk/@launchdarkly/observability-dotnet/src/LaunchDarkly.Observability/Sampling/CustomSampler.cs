using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using OpenTelemetry.Logs;

namespace LaunchDarkly.Observability.Sampling
{
    /// <summary>
    /// Volatile wrapper for the sampling config.
    /// This is a lock-free method to provide visibility of the config between threads.
    /// </summary>
    internal class ThreadSafeConfig
    {
        private SamplingConfig _config;

        public void SetSamplingConfig(SamplingConfig config)
        {
            Volatile.Write(ref _config, config);
        }

        public SamplingConfig GetSamplingConfig()
        {
            return Volatile.Read(ref _config);
        }
    }

    /// <summary>
    /// Function type for sampling decisions
    /// </summary>
    internal delegate bool SamplerFunc(int ratio);

    /// <summary>
    /// Default sampler implementation.
    /// </summary>
    internal static class DefaultSampler
    {
        public static bool Sample(int ratio)
        {
            return ThreadSafeSampler.Sample(ratio);
        }
    }

    internal class CustomSampler : IExportSampler
    {
        private readonly SamplerFunc _sampler;
        private readonly ThreadSafeConfig _config = new ThreadSafeConfig();
        private readonly ConcurrentDictionary<string, Regex> _regexCache = new ConcurrentDictionary<string, Regex>();

        private const string SamplingRatioAttribute = "launchdarkly.sampling.ratio";

        /// <summary>
        /// Delta between two numbers which will be considered equal.
        /// </summary>
        private const double Epsilon = 0.0000000000000001;

        /// <summary>
        /// Represents the result of sampling a span or log
        /// </summary>
        public CustomSampler(SamplerFunc sampler = null)
        {
            _sampler = sampler ?? DefaultSampler.Sample;
        }

        /// <summary>
        /// Set the sampling configuration.
        /// </summary>
        /// <param name="config">the new configuration</param>
        public void SetConfig(SamplingConfig config)
        {
            _config.SetSamplingConfig(config);
        }

        /// <summary>
        /// Check if sampling is enabled.
        /// <param>
        /// Sampling is enabled if there is at least one configuration in either the log or span sampling.
        /// </param>
        /// </summary>
        /// <returns>true if sampling is enabled</returns>
        public bool IsSamplingEnabled()
        {
            var config = _config.GetSamplingConfig();
            if (config == null) return false;

            var hasSpanConfig = config.Spans != null && config.Spans.Count > 0;
            var hasLogConfig = config.Logs != null && config.Logs.Count > 0;
            return hasLogConfig || hasSpanConfig;
        }

        private Regex GetCachedRegex(string pattern)
        {
            return _regexCache.GetOrAdd(pattern, p => new Regex(p, RegexOptions.Compiled));
        }

        private static bool IsMatchConfigEmpty(SamplingConfig.MatchConfig matchConfig)
        {
            return matchConfig == null ||
                   (matchConfig.MatchValue == null && string.IsNullOrEmpty(matchConfig.RegexValue));
        }

        private static bool CompareNumber(double a, object b)
        {
            switch (b)
            {
                case double d:
                    return Math.Abs(a - d) < Epsilon;
                case float f:
                    return Math.Abs(a - f) < Epsilon;
                case int i:
                    return Math.Abs(a - i) < Epsilon;
                case long l:
                    return Math.Abs(a - l) < Epsilon;
                default:
                    return false;
            }
        }

        private bool MatchesValue(SamplingConfig.MatchConfig matchConfig, object value)
        {
            if (IsMatchConfigEmpty(matchConfig) || value == null)
                return false;

            // Check basic match value
            if (matchConfig.MatchValue != null)
            {
                // Handle JsonElement from JSON deserialization
                if (matchConfig.MatchValue is JsonElement jsonElement)
                {
                    switch (jsonElement.ValueKind)
                    {
                        case JsonValueKind.String:
                            // GetString could throw if the type was not a string, but we make sure it is immediately
                            // before accessing it.
                            return jsonElement.GetString().Equals(value.ToString());
                        case JsonValueKind.Number:
                            if (jsonElement.TryGetDouble(out var doubleValue))
                                return CompareNumber(doubleValue, value);
                            break;
                        case JsonValueKind.True:
                            return true.Equals(value);
                        case JsonValueKind.False:
                            return false.Equals(value);
                        // Explicitly listed to demonstrate that these are not intended to be supported.
                        case JsonValueKind.Array:
                        case JsonValueKind.Undefined:
                        case JsonValueKind.Object:
                        case JsonValueKind.Null:
                        default:
                            break;
                    }
                }
                else
                {
                    return matchConfig.MatchValue.Equals(value);
                }
            }

            // Check regex match
            if (string.IsNullOrEmpty(matchConfig.RegexValue) || !(value is string stringValue)) return false;

            var regex = GetCachedRegex(matchConfig.RegexValue);
            return regex?.IsMatch(stringValue) ?? false;
        }

        private bool MatchesAttributes(List<SamplingConfig.AttributeMatchConfig> attributeConfigs,
            IList<KeyValuePair<string, object>> tags)
        {
            if (attributeConfigs.Count == 0) return true;
            if (tags == null || !tags.Any()) return false;

            foreach (var attrConfig in attributeConfigs)
            {
                var configMatched = false;
                foreach (var tag in tags)
                {
                    if (!MatchesValue(attrConfig.Key, tag.Key)) continue;
                    if (!MatchesValue(attrConfig.Attribute, tag.Value)) continue;
                    configMatched = true;
                    break;
                }

                if (!configMatched) return false;
            }

            return true;
        }

        private bool MatchesEvents(List<SamplingConfig.EventMatchConfig> eventConfigs,
            IList<ActivityEvent> events)
        {
            if (eventConfigs.Count == 0) return true;

            foreach (var eventConfig in eventConfigs)
            {
                var matched = false;
                foreach (var activityEvent in events)
                {
                    if (!MatchEvent(eventConfig, activityEvent)) continue;
                    matched = true;
                    break;
                }

                if (!matched) return false;
            }

            return true;
        }

        private bool MatchEvent(SamplingConfig.EventMatchConfig eventConfig, ActivityEvent activityEvent)
        {
            // Match by event name if specified
            if (!IsMatchConfigEmpty(eventConfig.Name))
            {
                if (!MatchesValue(eventConfig.Name, activityEvent.Name))
                    return false;
            }

            // Match by event attributes if specified
            return eventConfig.Attributes.Count <= 0 ||
                   MatchesAttributes(eventConfig.Attributes, activityEvent.Tags.ToList());
        }

        private bool MatchesSpanConfig(SamplingConfig.SpanSamplingConfig config, Activity span)
        {
            // Check span name if defined
            if (!IsMatchConfigEmpty(config.Name) && !MatchesValue(config.Name, span.DisplayName))
            {
                return false;
            }

            return MatchesAttributes(config.Attributes, span.TagObjects.ToList()) &&
                   // Check events  
                   MatchesEvents(config.Events, span.Events.ToList());
        }

        /// <summary>
        /// Sample a span.
        /// </summary>
        /// <param name="span">the span to sample</param>
        /// <returns>the sampling result</returns>
        public SamplingResult SampleSpan(Activity span)
        {
            var config = _config.GetSamplingConfig();
            if (!(config?.Spans?.Count > 0)) return new SamplingResult { Sample = true };
            foreach (var spanConfig in config.Spans)
            {
                if (MatchesSpanConfig(spanConfig, span))
                {
                    return new SamplingResult
                    {
                        Sample = _sampler(spanConfig.SamplingRatio),
                        Attributes = new Dictionary<string, object>
                        {
                            [SamplingRatioAttribute] = spanConfig.SamplingRatio
                        }
                    };
                }
            }

            // Default to sampling if no config matches
            return new SamplingResult { Sample = true };
        }

        private bool MatchesLogConfig(SamplingConfig.LogSamplingConfig config, LogRecord record)
        {
            // Check severity text if defined
            if (!IsMatchConfigEmpty(config.SeverityText))
            {
                if (!MatchesValue(config.SeverityText, record.LogLevel.ToString()))
                    return false;
            }

            // Check message if defined
            if (!IsMatchConfigEmpty(config.Message))
            {
                var message = record.FormattedMessage ?? record.Body;
                if (!MatchesValue(config.Message, message))
                    return false;
            }

            // Check attributes if defined
            if (config.Attributes.Count <= 0) return true;

            return record.Attributes != null && MatchesAttributes(config.Attributes, record.Attributes.ToList());
        }

        /// <summary>
        /// Sample a log record.
        /// </summary>
        /// <param name="record">the log record to sample</param>
        /// <returns>the sampling result</returns>
        public SamplingResult SampleLog(LogRecord record)
        {
            var config = _config.GetSamplingConfig();
            if (!(config?.Logs?.Count > 0)) return new SamplingResult { Sample = true };
            foreach (var logConfig in config.Logs)
            {
                if (MatchesLogConfig(logConfig, record))
                {
                    return new SamplingResult
                    {
                        Sample = _sampler(logConfig.SamplingRatio),
                        Attributes = new Dictionary<string, object>
                        {
                            [SamplingRatioAttribute] = logConfig.SamplingRatio
                        }
                    };
                }
            }

            // Default to sampling if no config matches
            return new SamplingResult { Sample = true };
        }
    }
}
