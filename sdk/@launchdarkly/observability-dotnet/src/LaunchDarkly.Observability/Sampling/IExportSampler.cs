using System.Collections.Generic;
using System.Diagnostics;
using OpenTelemetry.Logs;

namespace LaunchDarkly.Observability.Sampling
{
    internal class SamplingResult
    {
        public bool Sample { get; set; }
        public Dictionary<string, object> Attributes { get; set; } = new Dictionary<string, object>();
    }

    internal interface IExportSampler
    {
        SamplingResult SampleSpan(Activity span);
        SamplingResult SampleLog(LogRecord record);
        bool IsSamplingEnabled();
        void SetConfig(SamplingConfig config);
    }
}
