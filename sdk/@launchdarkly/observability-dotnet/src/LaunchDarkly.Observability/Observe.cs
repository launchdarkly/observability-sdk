using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.Diagnostics.Metrics;
using System.Threading;
using LaunchDarkly.Observability.Logging;
using Microsoft.Extensions.Logging;

namespace LaunchDarkly.Observability
{
    public static class Observe
    {
        private const string ErrorSpanName = "launchdarkly.error";

        private class Instance
        {
            public readonly Meter Meter;
            public readonly ActivitySource ActivitySource;
            public readonly ILogger Logger;

            public readonly ConcurrentDictionary<string, Counter<long>> Counters =
                new ConcurrentDictionary<string, Counter<long>>();

            public readonly ConcurrentDictionary<string, Gauge<double>> Gauges =
                new ConcurrentDictionary<string, Gauge<double>>();

            public readonly ConcurrentDictionary<string, Histogram<double>> Histograms =
                new ConcurrentDictionary<string, Histogram<double>>();

            public readonly ConcurrentDictionary<string, UpDownCounter<long>> UpDownCounters =
                new ConcurrentDictionary<string, UpDownCounter<long>>();

#if NETSTANDARD2_0 || NET5_0_OR_GREATER
            internal Instance(ObservabilityConfig config, ILoggerProvider loggerProvider)
            {
                Meter = new Meter(DefaultNames.MeterNameOrDefault(config.ServiceName),
                    config.ServiceVersion);
                ActivitySource = new ActivitySource(DefaultNames.ActivitySourceNameOrDefault(config.ServiceName),
                    config.ServiceVersion);
                if (loggerProvider != null)
                {
                    Logger = loggerProvider.CreateLogger(DefaultNames.LoggerNameOrDefault(config.ServiceName));
                }
            }
#endif

#if NETFRAMEWORK
            internal Instance(ObservabilityConfig config, ILogger logger) {
                Meter = new Meter(DefaultNames.MeterNameOrDefault(config.ServiceName), config.ServiceVersion);
                ActivitySource = new ActivitySource(DefaultNames.ActivitySourceNameOrDefault(config.ServiceName), config.ServiceVersion);
                Logger = logger;
            }
#endif
        }

        private static Instance _instance;

#if NETSTANDARD2_0 || NET5_0_OR_GREATER
        internal static void Initialize(ObservabilityConfig config, ILoggerProvider loggerProvider)
        {
            Volatile.Write(ref _instance, new Instance(config, loggerProvider));
        }
#endif

#if NETFRAMEWORK
        internal static void Initialize(ObservabilityConfig config, ILogger logger)
        {
            Volatile.Write(ref _instance, new Instance(config, logger));
        }
#endif

        private static Instance GetInstance()
        {
            return Volatile.Read(ref _instance);
        }

        private static void WithInstance(Action<Instance> action)
        {
            var instance = GetInstance();
            if (instance == null)
            {
                DebugLogger.DebugLog("Instance used before Observability Plugin initialized.");
                return;
            }

            action(instance);
        }

        private static KeyValuePair<string, object>[] ConvertToKeyValuePairs(IDictionary<string, object> dictionary)
        {
            if (dictionary == null || dictionary.Count == 0)
            {
                return null;
            }

            var result = new KeyValuePair<string, object>[dictionary.Count];
            var index = 0;
            foreach (var kvp in dictionary)
            {
                result[index] = new KeyValuePair<string, object>(kvp.Key, kvp.Value);
                index++;
            }

            return result;
        }

        /// <summary>
        /// Record an error in the active activity.
        /// <para>
        /// If there is not an active activity, then a new activity will be started and the error will be recorded
        /// into the activity.
        /// </para>
        /// </summary>
        /// <param name="exception">the exception to record</param>
        /// <param name="attributes">any additional attributes to add to the exception event</param>
        public static void RecordException(Exception exception, IDictionary<string, object> attributes = null)
        {
            if (exception == null)
            {
                DebugLogger.DebugLog("An attempt was made to record a null exception.");
                return;
            }

            WithInstance(instance =>
            {
                var activity = Activity.Current;
                var created = false;
                if (activity == null)
                {
                    activity = instance.ActivitySource.StartActivity(ErrorSpanName);
                    created = true;
                }

                if (attributes != null)
                {
                    var tags = new TagList(ConvertToKeyValuePairs(attributes));
                    activity?.AddException(exception, tags);
                }
                else
                {
                    activity?.AddException(exception);
                }

                if (created)
                {
                    activity?.Stop();
                }
            });
        }

        public static void RecordMetric(string name, double value, IDictionary<string, object> attributes = null)
        {
            if (name == null) throw new ArgumentNullException(nameof(name), "Metric name cannot be null.");

            WithInstance(instance =>
            {
                var gauge = instance.Gauges.GetOrAdd(name, (key) => instance.Meter.CreateGauge<double>(key));
                var keyValuePairs = ConvertToKeyValuePairs(attributes);
                if (keyValuePairs != null)
                {
                    gauge.Record(value, keyValuePairs);
                }
                else
                {
                    gauge.Record(value);
                }
            });
        }

        public static void RecordCount(string name, long value, IDictionary<string, object> attributes = null)
        {
            if (name == null) throw new ArgumentNullException(nameof(name), "Count name cannot be null.");

            WithInstance(instance =>
            {
                var count = instance.Counters.GetOrAdd(name, (key) => instance.Meter.CreateCounter<long>(key));
                var keyValuePairs = ConvertToKeyValuePairs(attributes);
                if (keyValuePairs != null)
                {
                    count.Add(value, keyValuePairs);
                }
                else
                {
                    count.Add(value);
                }
            });
        }

        public static void RecordIncr(string name, IDictionary<string, object> attributes = null)
        {
            RecordCount(name, 1, attributes);
        }

        public static void RecordHistogram(string name, double value, IDictionary<string, object> attributes = null)
        {
            if (name == null) throw new ArgumentNullException(nameof(name), "Histogram name cannot be null.");

            WithInstance(instance =>
            {
                var histogram =
                    instance.Histograms.GetOrAdd(name, (key) => instance.Meter.CreateHistogram<double>(key));
                var keyValuePairs = ConvertToKeyValuePairs(attributes);
                if (keyValuePairs != null)
                {
                    histogram.Record(value, keyValuePairs);
                }
                else
                {
                    histogram.Record(value);
                }
            });
        }

        public static void RecordUpDownCounter(string name, long delta, IDictionary<string, object> attributes = null)
        {
            if (name == null) throw new ArgumentNullException(nameof(name), "UpDownCounter name cannot be null.");

            WithInstance(instance =>
            {
                var upDownCounter =
                    instance.UpDownCounters.GetOrAdd(name, (key) => instance.Meter.CreateUpDownCounter<long>(key));

                var keyValuePairs = ConvertToKeyValuePairs(attributes);
                if (keyValuePairs != null)
                {
                    upDownCounter.Add(delta, keyValuePairs);
                }
                else
                {
                    upDownCounter.Add(delta);
                }
            });
        }

        public static void RecordLog(string message, LogLevel level, IDictionary<string, object> attributes)
        {
            WithInstance(instance =>
            {
                if (instance.Logger == null) return;
                if (attributes != null && attributes.Count > 0)
                    instance.Logger.Log(level, new EventId(), attributes, null,
                        (objects, exception) => message);
                else
                    instance.Logger.Log<object>(level, new EventId(), null, null,
                        (objects, exception) => message);
            });
        }

        public static Activity StartActivity(string name, ActivityKind kind = ActivityKind.Internal,
            IDictionary<string, object> attributes = null)
        {
            var instance = GetInstance();
            if (instance == null) return null;
            var activity = instance.ActivitySource.StartActivity(name, kind);
            if (attributes != null)
            {
                foreach (var attribute in attributes)
                {
                    activity?.AddTag(attribute.Key, attribute.Value);
                }
            }

            return activity;
        }
    }
}
