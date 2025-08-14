using System.Collections.Generic;
using LaunchDarkly.Observability.Sampling;
using OpenTelemetry;
using OpenTelemetry.Logs;

namespace LaunchDarkly.Observability.Otel
{
    /// <summary>
    /// In dotnet logs cannot be sampled at export time because the log exporter cannot be effectively
    /// wrapper. The log exporter is a sealed class, which prevents inheritance, and it also has
    /// internal methods which are accessed by other otel components. These internal methods mean
    /// that it is not possible to use composition and delegate to the base exporter.
    /// </summary>
    internal class SamplingLogProcessor : BaseProcessor<LogRecord>
    {
        private readonly IExportSampler _sampler;

        public SamplingLogProcessor(IExportSampler sampler)
        {
            _sampler = sampler;
        }

        public override void OnEnd(LogRecord data)
        {
            var res = _sampler.SampleLog(data);
            if (!res.Sample) return;
            if (res.Attributes != null && res.Attributes.Count > 0)
            {
                var combinedAttributes = new List<KeyValuePair<string, object>>(res.Attributes);
                if (data.Attributes != null) combinedAttributes.AddRange(data.Attributes);

                data.Attributes = combinedAttributes;
            }

            base.OnEnd(data);
        }
    }
}
