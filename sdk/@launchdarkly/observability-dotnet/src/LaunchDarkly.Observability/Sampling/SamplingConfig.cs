using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace LaunchDarkly.Observability.Sampling
{
    internal class SamplingConfig
    {
        [JsonPropertyName("spans")]
        public List<SpanSamplingConfig> Spans { get; set; } = new List<SpanSamplingConfig>();

        [JsonPropertyName("logs")] public List<LogSamplingConfig> Logs { get; set; } = new List<LogSamplingConfig>();

        internal class MatchConfig
        {
            [JsonPropertyName("matchValue")] public object MatchValue { get; set; }

            [JsonPropertyName("regexValue")] public string RegexValue { get; set; }
        }

        internal class AttributeMatchConfig
        {
            [JsonPropertyName("key")] public MatchConfig Key { get; set; }

            [JsonPropertyName("attribute")] public MatchConfig Attribute { get; set; }
        }

        internal class EventMatchConfig
        {
            [JsonPropertyName("name")] public MatchConfig Name { get; set; }

            [JsonPropertyName("attributes")]
            public List<AttributeMatchConfig> Attributes { get; set; } = new List<AttributeMatchConfig>();
        }

        internal class SpanSamplingConfig
        {
            [JsonPropertyName("name")] public MatchConfig Name { get; set; }

            [JsonPropertyName("attributes")]
            public List<AttributeMatchConfig> Attributes { get; set; } = new List<AttributeMatchConfig>();

            [JsonPropertyName("events")]
            public List<EventMatchConfig> Events { get; set; } = new List<EventMatchConfig>();

            [JsonPropertyName("samplingRatio")] public int SamplingRatio { get; set; } = 1;
        }

        internal class LogSamplingConfig
        {
            [JsonPropertyName("severityText")] public MatchConfig SeverityText { get; set; }

            [JsonPropertyName("message")] public MatchConfig Message { get; set; }

            [JsonPropertyName("attributes")]
            public List<AttributeMatchConfig> Attributes { get; set; } = new List<AttributeMatchConfig>();

            [JsonPropertyName("samplingRatio")] public int SamplingRatio { get; set; } = 1;
        }
    }
}
