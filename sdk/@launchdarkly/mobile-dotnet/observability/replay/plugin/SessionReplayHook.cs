using System.Collections.Immutable;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    using SeriesData = ImmutableDictionary<string, object>;

    /// <summary>
    /// Hook that delegates identify calls to the native
    /// SessionReplayHookProxy (via NativeSessionReplayHookExporter),
    /// and forwards flag evaluation data to LDReplay.
    /// </summary>
    internal sealed class SessionReplayHook : Hook
    {
        private readonly SessionReplayService _sessionReplayService;
        private NativeSessionReplayHookExporter? _nativeHookExporter;
        private bool _nativeHookExporterResolved;

        internal SessionReplayHook(SessionReplayService sessionReplayService)
            : base("LaunchDarkly.SessionReplay")
        {
            _sessionReplayService = sessionReplayService;
        }

        private NativeSessionReplayHookExporter? NativeHookExporter
        {
            get
            {
                if (!_nativeHookExporterResolved)
                {
                    _nativeHookExporter = _sessionReplayService.GetNativeSessionReplayHookExporter();
                    _nativeHookExporterResolved = true;
                }
                return _nativeHookExporter;
            }
        }

        public override SeriesData BeforeEvaluation(EvaluationSeriesContext context, SeriesData data)
        {
            return data;
        }

        public override SeriesData AfterEvaluation(EvaluationSeriesContext context, SeriesData data,
            EvaluationDetail<LdValue> detail)
        {
            return data;
        }

        public override SeriesData BeforeIdentify(IdentifySeriesContext context, SeriesData data)
        {
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
