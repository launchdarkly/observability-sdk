using System.Collections.Immutable;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    using SeriesData = ImmutableDictionary<string, object>;

    /// <summary>
    /// Hook that forwards flag evaluation data to the session replay native bridge,
    /// allowing evaluations to be associated with recorded sessions.
    /// </summary>
    internal sealed class SessionReplayHook : Hook
    {
        private readonly NativeSessionReplay _nativeSessionReplay;

        internal SessionReplayHook(NativeSessionReplay nativeSessionReplay)
            : base("LaunchDarkly.SessionReplay")
        {
            _nativeSessionReplay = nativeSessionReplay;
        }

        public override SeriesData BeforeEvaluation(EvaluationSeriesContext context, SeriesData data)
        {
            return data;
        }

        public override SeriesData AfterEvaluation(EvaluationSeriesContext context, SeriesData data,
            EvaluationDetail<LdValue> detail)
        {
            LDReplay.TrackEvaluation(
                context.FlagKey,
                detail.Value,
                detail.VariationIndex,
                detail.Reason
            );

            return data;
        }

        public override SeriesData BeforeIdentify(IdentifySeriesContext context, SeriesData data)
        {
            return data;
        }

        public override SeriesData AfterIdentify(IdentifySeriesContext context, SeriesData data,
            IdentifySeriesResult result)
        {
            return data;
        }
    }
}
