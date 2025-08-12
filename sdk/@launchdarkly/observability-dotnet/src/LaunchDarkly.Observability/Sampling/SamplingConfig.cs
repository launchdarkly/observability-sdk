using System.Collections.Generic;

namespace LaunchDarkly.Observability.Sampling
{
    internal class SamplingConfig
    {
        public class MatchConfig
        {
            public object MatchValue { get; }
            public string RegexValue { get; }
        }

        public class AttributeMatchConfig
        {
            public MatchConfig Key { get; set; }
            public MatchConfig Attribute { get; set; }
        }

        public class EventMatchConfig
        {
            public MatchConfig Name { get; set; }
            public List<AttributeMatchConfig> Attributes { get; set; }
        }

        /// <summary>
        /// Span sampling configuration
        /// </summary>
        public class SpanSamplingConfig
        {
            public MatchConfig Name { get; set; } 
            public List<AttributeMatchConfig> Attributes { get; set; } 
            public List<EventMatchConfig> Events { get; set; } 
            public int SamplingRatio { get; set; } = 1;
        }

        /// <summary>
        /// Log sampling configuration
        /// </summary>
        public class LogSamplingConfig
        {
            public MatchConfig SeverityText { get; set; } 
            public MatchConfig Message { get; set; } 
            public List<AttributeMatchConfig> Attributes { get; set; } 
            public int SamplingRatio { get; set; } = 1;
        }

        public List<SpanSamplingConfig> Spans { get; set; } 
        public List<LogSamplingConfig> Logs { get; set; } 
    }
}
