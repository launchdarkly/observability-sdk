using System.Collections.Immutable;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    using SeriesData = ImmutableDictionary<string, object>;

    /// <summary>
    /// Hook that delegates evaluation and identify calls to the native
    /// ObservabilityHookImplementation (via NativeHookExporter) on iOS,
    /// and is a no-op on other platforms.
    /// </summary>
    internal sealed class ObservabilityHook : Hook
    {
        private readonly NativeObserve _nativeObserve;
        private NativeHookExporter? _nativeHookExporter;
        private bool _nativeHookExporterResolved;

        internal ObservabilityHook(NativeObserve nativeObserve)
            : base("LaunchDarkly.Observability")
        {
            _nativeObserve = nativeObserve;
        }

        private NativeHookExporter? NativeHookExporter
        {
            get
            {
                if (!_nativeHookExporterResolved)
                {
                    _nativeHookExporter = _nativeObserve.GetNativeHookExporter();
                    _nativeHookExporterResolved = true;
                }
                return _nativeHookExporter;
            }
        }

        public override SeriesData BeforeEvaluation(EvaluationSeriesContext context, SeriesData data)
        {
            if (NativeHookExporter != null)
            {
                data = NativeHookExporter.BeforeEvaluation(context, data);
            }
            return data;
        }

        public override SeriesData AfterEvaluation(EvaluationSeriesContext context, SeriesData data,
            EvaluationDetail<LdValue> detail)
        {
            if (NativeHookExporter != null)
            {
                data = NativeHookExporter.AfterEvaluation(context, data, detail);
            }
            return data;
        }

        public override SeriesData BeforeIdentify(IdentifySeriesContext context, SeriesData data)
        {
            if (NativeHookExporter != null)
            {
                data = NativeHookExporter.BeforeIdentify(context, data);
            }
            return data;
        }

        public override SeriesData AfterIdentify(IdentifySeriesContext context, SeriesData data,
            IdentifySeriesResult result)
        {
            if (NativeHookExporter != null)
            {
                data = NativeHookExporter.AfterIdentify(context, data, result);
            }
            return data;
        }
    }
}
