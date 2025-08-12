using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
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
            Volatile.Read(ref _config);
            return _config;
        }
    }

    /// <summary>
    /// Function type for sampling decisions
    /// </summary>
    public delegate bool SamplerFunc(int ratio);

    /// <summary>
    /// Default sampler function similar to the Go implementation
    /// </summary>
    public static class DefaultSampler
    {
        public static bool Sample(int ratio)
        {
            return ThreadSafeSampler.Sample(ratio);
        }
    }

    /// <summary>
    /// Custom sampler implementation similar to the Go version
    /// </summary>
    internal class CustomSampler : IExportSampler
    {
        private readonly SamplerFunc _sampler;
        private readonly ThreadSafeConfig _config = new ThreadSafeConfig();
        private readonly Dictionary<string, Regex> _regexCache = new Dictionary<string, Regex>();

        private readonly object _regexLock = new object();

        /// <summary>
        /// Represents the result of sampling a span or log
        /// </summary>
        public CustomSampler(SamplerFunc sampler = null)
        {
            _sampler = sampler ?? DefaultSampler.Sample;
        }

        public void SetConfig(SamplingConfig config)
        {
            _config.SetSamplingConfig(config);
        }

        public bool IsSamplingEnabled()
        {
            var config = _config.GetSamplingConfig();
            if (config == null) return false;

            var hasSpanConfig = config.Spans != null && config.Spans.Count > 0;
            var hasLogConfig = config.Logs != null && config.Logs.Count > 0;
            return hasLogConfig && hasSpanConfig;
        }

        private Regex GetCachedRegex(string pattern)
        {
            lock (_regexLock)
            {
                if (_regexCache.TryGetValue(pattern, out var cachedRegex))
                    return cachedRegex;

                var regex = new Regex(pattern, RegexOptions.Compiled);
                _regexCache[pattern] = regex;
                return regex;
            }
        }

        private static bool IsMatchConfigEmpty(SamplingConfig.MatchConfig matchConfig)
        {
            return matchConfig == null ||
                   (matchConfig.MatchValue == null && string.IsNullOrEmpty(matchConfig.RegexValue));
        }

        private bool MatchesValue(SamplingConfig.MatchConfig matchConfig, object value)
        {
            if (IsMatchConfigEmpty(matchConfig) || value == null)
                return false;

            // Check basic match value
            if (matchConfig.MatchValue != null)
            {
                return matchConfig.MatchValue.Equals(value);
            }

            // Check regex match
            if (string.IsNullOrEmpty(matchConfig.RegexValue) || !(value is string stringValue)) return false;

            var regex = GetCachedRegex(matchConfig.RegexValue);
            return regex?.IsMatch(stringValue) ?? false;
        }

        private bool MatchesAttributes(List<SamplingConfig.AttributeMatchConfig> attributeConfigs,
            ActivityTagsCollection tags)
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
            if (eventConfig.Attributes.Count <= 0) return true;
            var eventTags = new ActivityTagsCollection();
            foreach (var tag in activityEvent.Tags)
            {
                eventTags.Add(tag.Key, tag.Value);
            }

            return MatchesAttributes(eventConfig.Attributes, eventTags);
        }

        private bool MatchesSpanConfig(SamplingConfig.SpanSamplingConfig config, Activity span)
        {
            // Check span name if defined
            if (!IsMatchConfigEmpty(config.Name) && !MatchesValue(config.Name, span.DisplayName))
            {
                return false;
            }

            // Check attributes
            var spanTags = new ActivityTagsCollection();
            foreach (var tag in span.Tags)
            {
                spanTags.Add(tag.Key, tag.Value);
            }

            return MatchesAttributes(config.Attributes, spanTags) &&
                   // Check events  
                   MatchesEvents(config.Events, span.Events.ToList());
        }

        public SamplingResult SampleSpan(Activity span)
        {
            var config = _config.GetSamplingConfig();
            if (!(config?.Spans.Count > 0)) return new SamplingResult { Sample = true };
            foreach (var spanConfig in config.Spans)
            {
                if (MatchesSpanConfig(spanConfig, span))
                {
                    return new SamplingResult
                    {
                        Sample = _sampler(spanConfig.SamplingRatio),
                        Attributes = new Dictionary<string, object>
                        {
                            ["sampling.ratio"] = spanConfig.SamplingRatio
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
            // Convert log record attributes to format we can check
            var logAttributes = new ActivityTagsCollection();
            if (record.Attributes != null)
            {
                foreach (var attr in record.Attributes)
                {
                    logAttributes.Add(attr.Key, attr.Value);
                }
            }

            return MatchesAttributes(config.Attributes, logAttributes);
        }

        public LogSamplingResult SampleLog(LogRecord record)
        {
            var config = _config.GetSamplingConfig();
            if (!(config?.Logs.Count > 0)) return new LogSamplingResult { Sample = true };
            foreach (var logConfig in config.Logs)
            {
                if (MatchesLogConfig(logConfig, record))
                {
                    return new LogSamplingResult
                    {
                        Sample = _sampler(logConfig.SamplingRatio),
                        Attributes = new Dictionary<string, object>
                        {
                            ["sampling.ratio"] = logConfig.SamplingRatio
                        }
                    };
                }
            }

            // Default to sampling if no config matches
            return new LogSamplingResult { Sample = true };
        }
    }
}
    
