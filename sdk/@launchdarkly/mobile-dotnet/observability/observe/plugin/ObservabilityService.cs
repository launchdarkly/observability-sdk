using System;
using System.Collections.Generic;
using System.Diagnostics;
using LaunchDarkly.Sdk.Client.Interfaces;
using LaunchDarkly.Sdk.Integrations.Plugins;
using OpenTelemetry.Trace;

#if IOS
using Foundation;
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.Observability
{
    internal class ObservabilityService : INativePlugin
    {
        private static readonly Tracer NoopTracer =
            TracerProvider.Default.GetTracer("noop");

        private static readonly ActivitySource NoopActivitySource = new("noop");

#if ANDROID
        private readonly LDObserveAndroid.ObservabilityBridge _androidBridge = new();
#endif

        private LDTracer? _tracer;

#if IOS
        private ObjcLogger? _nativeLogger;
#elif ANDROID
        private LDObserveAndroid.RealLogger? _nativeLogger;
#endif

        internal ObservabilityOptions Options { get; }
        internal ILdClient? Client { get; set; }
        internal EnvironmentMetadata? Metadata { get; set; }

        internal ObservabilityService(ObservabilityOptions options)
        {
            Options = options;
        }

        internal void Initialize()
        {
            _tracer?.Dispose();
            _tracer = new LDTracer(Options.ServiceName, Options.Instrumentation.NetworkRequests);

#if IOS
            _nativeLogger = LDObserveBridge.GetObjcLogger();
#elif ANDROID
            _nativeLogger = LDObserveAndroid.LDObserveBridgeAdapter.Logger;
#endif
        }

        internal Tracer GetTracer() => _tracer?.Tracer ?? NoopTracer;

        internal TelemetrySpan StartActiveSpan(string name) => GetTracer().StartActiveSpan(name);

        internal TelemetrySpan StartActiveSpan(string name, SpanKind kind, SpanContext parentContext)
            => GetTracer().StartActiveSpan(name, kind, parentContext);

        internal TelemetrySpan StartRootSpan(string name) => GetTracer().StartRootSpan(name);

        internal ActivitySource GetActivitySource() => _tracer?.ActivitySource ?? NoopActivitySource;

        internal Activity? StartActivity(string name) => GetActivitySource().StartActivity(name);

        internal Activity? StartRootActivity(string name)
        {
            var previous = Activity.Current;
            Activity.Current = null;
            try
            {
                return GetActivitySource().StartActivity(name);
            }
            finally
            {
                Activity.Current = previous?.IsStopped == true ? null : previous;
            }
        }

        internal void RecordLog(
            string message,
            Severity severity,
            IDictionary<string, object?>? attributes = null,
            SpanContext? spanContext = null)
        {
            if (_nativeLogger == null) return;

            string? traceId;
            string? spanId;
            if (spanContext is { IsValid: true })
            {
                traceId = spanContext.Value.TraceId.ToString();
                spanId = spanContext.Value.SpanId.ToString();
            }
            else
            {
                traceId = Activity.Current?.TraceId.ToString();
                spanId = Activity.Current?.SpanId.ToString();
            }

#if IOS
            var dict = DictionaryTypeConverters.ToNSDictionary(attributes) ?? new NSDictionary();
            _nativeLogger.RecordLog(message, (nint)severity, traceId, spanId, false, dict);
#elif ANDROID
            var map = DictionaryTypeConverters.ToJavaDictionary(attributes);
            _nativeLogger.RecordLog(message, (int)severity, traceId, spanId, false, map);
#endif
        }

        internal void RecordError(
            Exception exception,
            IDictionary<string, object?>? attributes = null)
        {
            RecordError(exception.Message, exception.ToString());

            if (attributes is { Count: > 0 })
            {
                using var span = GetTracer().StartActiveSpan("highlight.error");
                foreach (var attr in attributes)
                {
                    if (attr.Value != null)
                        span.SetAttribute(attr.Key, attr.Value.ToString()!);
                }
                span.RecordException(exception);
            }
        }

        internal void RecordError(string message, string? cause = null)
        {
#if IOS
            LDObserveBridge.RecordError(message, cause);
#elif ANDROID
            _androidBridge.RecordError(message, cause);
#endif
        }

        internal void RecordMetric(string name, double value)
        {
#if IOS
            LDObserveBridge.RecordMetric(name, value);
#elif ANDROID
            _androidBridge.RecordMetric(name, value);
#endif
        }

        internal void RecordCount(string name, double value)
        {
#if IOS
            LDObserveBridge.RecordCount(name, value);
#elif ANDROID
            _androidBridge.RecordCount(name, value);
#endif
        }

        internal void RecordIncr(string name, double value)
        {
#if IOS
            LDObserveBridge.RecordIncr(name, value);
#elif ANDROID
            _androidBridge.RecordIncr(name, value);
#endif
        }

        internal void RecordHistogram(string name, double value)
        {
#if IOS
            LDObserveBridge.RecordHistogram(name, value);
#elif ANDROID
            _androidBridge.RecordHistogram(name, value);
#endif
        }

        internal void RecordUpDownCounter(string name, double value)
        {
#if IOS
            LDObserveBridge.RecordUpDownCounter(name, value);
#elif ANDROID
            _androidBridge.RecordUpDownCounter(name, value);
#endif
        }

        internal NativeObservabilityHookExporter? GetNativeHookExporter()
        {
#if IOS
            var proxy = LDObserveMaciOS.LDObserveBridge.GetObservabilityHookProxy();
            if (proxy != null)
            {
                return new NativeObservabilityHookExporter(proxy);
            }
#elif ANDROID
            var proxy = LDObserveAndroid.LDObserveBridgeAdapter.ObservabilityHookProxy;
            if (proxy != null)
            {
                return new NativeObservabilityHookExporter(proxy);
            }
#endif
            return null;
        }
    }
}
