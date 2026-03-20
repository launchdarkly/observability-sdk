using System;
using System.Diagnostics;
using OpenTelemetry;

namespace LaunchDarkly.Observability;

/// <summary>
/// Custom OpenTelemetry trace exporter that writes span data to the console.
/// </summary>
public sealed class LDTraceExporter : BaseExporter<Activity>
{
    public override ExportResult Export(in Batch<Activity> batch)
    {
        foreach (var activity in batch)
        {
            Console.WriteLine($"[LDTraceExporter] Span: {activity.DisplayName}");
            Console.WriteLine($"  TraceId:    {activity.TraceId}");
            Console.WriteLine($"  SpanId:     {activity.SpanId}");
            Console.WriteLine($"  StartTime:  {activity.StartTimeUtc:O}");
            Console.WriteLine($"  Duration:   {activity.Duration}");
            Console.WriteLine($"  Status:     {activity.Status}");
            Console.WriteLine($"  Parent:     {activity.Parent}");

            if (activity.TagObjects is not null)
            {
                foreach (var tag in activity.TagObjects)
                {
                    Console.WriteLine($"  Attribute:  {tag.Key} = {tag.Value}");
                }
            }

            if (activity.Events is not null)
            {
                foreach (var evt in activity.Events)
                {
                    Console.WriteLine($"  Event:      {evt.Name} @ {evt.Timestamp:O}");
                }
            }

            Console.WriteLine();
        }

        return ExportResult.Success;
    }
}
