using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.Diagnostics.Metrics;
using System.Threading;
using OpenTelemetry.Metrics;

namespace LaunchDarkly.Observability
{
    public static class Observe
    {
        private const string ErrorSpanName = "launchdarkly.error";

        private class Instance
        {
            public readonly Meter Meter;
            public readonly ActivitySource ActivitySource;

            public readonly ConcurrentDictionary<string, Counter<long>> Counters =
                new ConcurrentDictionary<string, Counter<long>>();

            public readonly ConcurrentDictionary<string, Gauge<double>> Gauges =
                new ConcurrentDictionary<string, Gauge<double>>();

            public readonly ConcurrentDictionary<string, Histogram<double>> Histograms =
                new ConcurrentDictionary<string, Histogram<double>>();

            public readonly ConcurrentDictionary<string, UpDownCounter<long>> UpDownCounters =
                new ConcurrentDictionary<string, UpDownCounter<long>>();

            internal Instance(ObservabilityConfig config)
            {
                Meter = new Meter(config.ServiceName ?? DefaultNames.MeterName,
                    config.ServiceVersion);
                ActivitySource = new ActivitySource(config.ServiceName ?? DefaultNames.ActivitySourceName,
                    config.ServiceVersion);
            }
        }

        private static Instance _instance;

        internal static void Initialize(ObservabilityConfig config)
        {
            Volatile.Write(ref _instance, new Instance(config));
        }

        private static Instance GetInstance()
        {
            return Volatile.Read(ref _instance);
        }

        private static void WithInstance(Action<Instance> action)
        {
            var instance = GetInstance();
            if (instance == null)
            {
                // TODO: Log after PR with logger merged.
                return;
            }

            action(instance);
        }

        public static void RecordError(Exception exception)
        {
            WithInstance(instance =>
            {
                var activity = Activity.Current;
                var created = false;
                if (activity == null)
                {
                    activity = instance.ActivitySource.StartActivity(ErrorSpanName);
                    created = true;
                }

                activity?.AddException(exception);
                if (created)
                {
                    activity?.Stop();
                }
            });
        }

        public static void RecordMetric(string name, double value, TagList? tags = null)
        {
            if (name == null) throw new ArgumentNullException(nameof(name), "Metric name cannot be null.");

            WithInstance(instance =>
            {
                var gauge = instance.Gauges.GetOrAdd(name, (key) => instance.Meter.CreateGauge<double>(key));
                if (tags.HasValue)
                {
                    gauge.Record(value, tags.Value);
                }
                else
                {
                    gauge.Record(value);
                }
            });
        }

        public static void RecordCount(string name, long value, TagList? tags = null)
        {
            if (name == null) throw new ArgumentNullException(nameof(name), "Count name cannot be null.");

            WithInstance(instance =>
            {
                var count = instance.Counters.GetOrAdd(name, (key) => instance.Meter.CreateCounter<long>(key));
                if (tags.HasValue)
                {
                    count.Add(value, tags.Value);
                }
                else
                {
                    count.Add(value);
                }
            });
        }

        public static void RecordIncr(string name, TagList? tags = null)
        {
            RecordCount(name, 1, tags);
        }

        public static void RecordHistogram(string name, double value, TagList? tags = null)
        {
            if (name == null) throw new ArgumentNullException(nameof(name), "Histogram name cannot be null.");

            WithInstance(instance =>
            {
                var histogram =
                    instance.Histograms.GetOrAdd(name, (key) => instance.Meter.CreateHistogram<double>(key));
                if (tags != null)
                {
                    histogram.Record(value, tags.Value);
                }
                else
                {
                    histogram.Record(value);
                }
            });
        }

        public static void RecordUpDownCounter(string name, long delta, TagList? tags = null)
        {
            if (name == null) throw new ArgumentNullException(nameof(name), "UpDownCounter name cannot be null.");

            WithInstance(instance =>
            {
                var upDownCounter =
                    instance.UpDownCounters.GetOrAdd(name, (key) => instance.Meter.CreateUpDownCounter<long>(key));

                if (tags != null)
                {
                    upDownCounter.Add(delta, tags.Value);
                }
                else
                {
                    upDownCounter.Add(delta);
                }
            });
        }

        public static void RecordLog(string message, string severityText, IDictionary<string, object> attributes)
        {
        }

        public static Activity StartActivity(string name, ActivityKind kind = ActivityKind.Internal, IDictionary<string, object> attributes = null)
        {
            var instance = GetInstance();
            if (instance == null) return null;
            var activity = instance.ActivitySource.StartActivity(name, kind);
            if (attributes == null) return null;
            foreach (var attribute in attributes)
            {
                activity?.AddTag(attribute.Key, attribute.Value);
            }
            // TODO: Do we need to log?
            return null;
        }
    }
}
