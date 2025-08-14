using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;

namespace LaunchDarkly.Observability.Sampling
{
    /// <summary>
    ///     Utilities for sampling spans including hierarchical span sampling
    /// </summary>
    internal static class SampleSpans
    {
        /// <summary>
        ///     Sample spans with hierarchical logic that removes children of sampled-out spans
        /// </summary>
        /// <param name="activities">Collection of activities to sample</param>
        /// <param name="sampler">The sampler to use for sampling decisions</param>
        /// <returns>List of sampled activities</returns>
        public static List<Activity> SampleActivities(IEnumerable<Activity> activities, IExportSampler sampler)
        {
            if (!sampler.IsSamplingEnabled()) return activities.ToList();

            var omittedSpanIds = new List<string>();
            var activityById = new Dictionary<string, Activity>();
            var childrenByParentId = new Dictionary<string, List<string>>();

            // First pass: sample items which are directly impacted by a sampling decision
            // and build a map of children spans by parent span id
            foreach (var activity in activities)
            {
                var spanId = activity.SpanId.ToString();

                // Build parent-child relationship map
                if (activity.ParentSpanId != default)
                {
                    var parentSpanId = activity.ParentSpanId.ToString();
                    if (!childrenByParentId.ContainsKey(parentSpanId))
                        childrenByParentId[parentSpanId] = new List<string>();

                    childrenByParentId[parentSpanId].Add(spanId);
                }

                // Sample the span
                var sampleResult = sampler.SampleSpan(activity);
                if (sampleResult.Sample)
                {
                    if (sampleResult.Attributes != null && sampleResult.Attributes.Count > 0)
                        foreach (var attr in sampleResult.Attributes)
                            activity.SetTag(attr.Key, attr.Value);

                    activityById[spanId] = activity;
                }
                else
                {
                    omittedSpanIds.Add(spanId);
                }
            }

            // Find all children of spans that have been sampled out and remove them
            // Repeat until there are no more children to remove
            while (omittedSpanIds.Count > 0)
            {
                var spanId = omittedSpanIds[0];
                omittedSpanIds.RemoveAt(0);

                if (!childrenByParentId.TryGetValue(spanId, out var affectedSpans)) continue;
                foreach (var spanIdToRemove in affectedSpans)
                {
                    activityById.Remove(spanIdToRemove);
                    omittedSpanIds.Add(spanIdToRemove);
                }
            }

            return activityById.Values.ToList();
        }
    }
}
