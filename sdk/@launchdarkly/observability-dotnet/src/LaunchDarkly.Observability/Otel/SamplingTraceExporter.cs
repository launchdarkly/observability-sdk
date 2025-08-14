using System.Collections.Generic;
using System.Diagnostics;
using LaunchDarkly.Observability.Sampling;
using OpenTelemetry;
using OpenTelemetry.Exporter;

namespace LaunchDarkly.Observability.Otel
{
    /// <summary>
    /// Custom trace exporter that applies sampling before exporting
    /// </summary>
    internal class SamplingTraceExporter : OtlpTraceExporter
    {
        private readonly IExportSampler _sampler;

        public SamplingTraceExporter(IExportSampler sampler, OtlpExporterOptions options): base(options)
        {
            _sampler = sampler;
        }

        public override ExportResult Export(in Batch<Activity> batch)
        {
            if (!_sampler.IsSamplingEnabled())
            {
                return base.Export(batch);
            }

            // Convert batch to enumerable and use the new hierarchical sampling logic
            var activities = new List<Activity>();
            foreach (var activity in batch)
            {
                activities.Add(activity);
            }
            var sampledActivities = SampleSpans.SampleActivities(activities, _sampler);

            if (sampledActivities.Count == 0)
                return ExportResult.Success;

            // Create a new batch with only the sampled activities
            using (var sampledBatch = new Batch<Activity>(sampledActivities.ToArray(), sampledActivities.Count))
            {
                return base.Export(sampledBatch);
            }
        }
    }
}
