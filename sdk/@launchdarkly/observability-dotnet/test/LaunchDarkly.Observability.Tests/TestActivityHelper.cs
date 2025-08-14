using System.Diagnostics;

namespace LaunchDarkly.Observability.Test
{
    /// <summary>
    /// Helper class for creating test activities
    /// </summary>
    internal static class TestActivityHelper
    {
        /// <summary>
        /// Creates a test Activity with the specified name and optional parent span ID
        /// </summary>
        /// <param name="name">The name/display name for the activity</param>
        /// <param name="parentSpanId">Optional parent span ID to create a child relationship</param>
        /// <returns>A stopped Activity ready for testing</returns>
        internal static Activity CreateTestActivity(string name, string parentSpanId = null)
        {
            var activity = new Activity(name);
            activity.Start();
            activity.DisplayName = name;

            // Set W3C format for consistent trace/span ID generation
            activity.SetIdFormat(ActivityIdFormat.W3C);

            // If we have a parent span ID, we need to create a proper parent context
            if (!string.IsNullOrEmpty(parentSpanId))
            {
                // Create a trace ID and parent span context
                var traceId = ActivityTraceId.CreateRandom();
                var parentSpan = ActivitySpanId.CreateFromString(parentSpanId.PadRight(16, '0'));
                var parentContext = new ActivityContext(traceId, parentSpan, ActivityTraceFlags.Recorded);

                // Stop and recreate the activity with the parent context
                activity.Stop();
                activity = new Activity(name);
                activity.SetParentId(parentContext.TraceId, parentContext.SpanId, parentContext.TraceFlags);
                activity.Start();
                activity.DisplayName = name;
            }

            activity.Stop();
            return activity;
        }
    }
}