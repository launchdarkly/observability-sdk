using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace LaunchDarkly.Observability.Sampling
{
    public class SamplingConfig
    {
        public class MatchConfig
        {
            [JsonPropertyName("matchValue")]
            public object MatchValue { get; set; }
            
            [JsonPropertyName("regexValue")]
            public string RegexValue { get; set; }
        }

        public class AttributeMatchConfig
        {
            [JsonPropertyName("key")]
            public MatchConfig Key { get; set; }
            
            [JsonPropertyName("attribute")]
            public MatchConfig Attribute { get; set; }
        }

        public class EventMatchConfig
        {
            [JsonPropertyName("name")]
            public MatchConfig Name { get; set; }
            
            [JsonPropertyName("attributes")]
            public List<AttributeMatchConfig> Attributes { get; set; } = new List<AttributeMatchConfig>();
        }

        /// <summary>
        /// Span sampling configuration
        /// </summary>
        public class SpanSamplingConfig
        {
            [JsonPropertyName("name")]
            public MatchConfig Name { get; set; }
            
            [JsonPropertyName("attributes")]
            public List<AttributeMatchConfig> Attributes { get; set; } = new List<AttributeMatchConfig>();
            
            [JsonPropertyName("events")]
            public List<EventMatchConfig> Events { get; set; } = new List<EventMatchConfig>();
            
            [JsonPropertyName("samplingRatio")]
            public int SamplingRatio { get; set; } = 1;
        }

        /// <summary>
        /// Log sampling configuration
        /// </summary>
        public class LogSamplingConfig
        {
            [JsonPropertyName("severityText")]
            public MatchConfig SeverityText { get; set; }
            
            [JsonPropertyName("message")]
            public MatchConfig Message { get; set; }
            
            [JsonPropertyName("attributes")]
            public List<AttributeMatchConfig> Attributes { get; set; } = new List<AttributeMatchConfig>();
            
            [JsonPropertyName("samplingRatio")]
            public int SamplingRatio { get; set; } = 1;
        }

        [JsonPropertyName("spans")]
        public List<SpanSamplingConfig> Spans { get; set; } = new List<SpanSamplingConfig>();
        
        [JsonPropertyName("logs")]
        public List<LogSamplingConfig> Logs { get; set; } = new List<LogSamplingConfig>(); 
    }
}
