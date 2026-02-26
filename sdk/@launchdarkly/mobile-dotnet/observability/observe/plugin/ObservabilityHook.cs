using System.Collections.Immutable;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    using SeriesData = ImmutableDictionary<string, object>;

    /// <summary>
    /// Hook that records flag evaluation data for observability tracing,
    /// forwarding evaluation details to the native observability bridge.
    /// </summary>
    internal sealed class ObservabilityHook : Hook
    {
        private readonly NativeObserve _nativeObserve;

        internal ObservabilityHook(NativeObserve nativeObserve)
            : base("LaunchDarkly.Observability")
        {
            _nativeObserve = nativeObserve;
        }

        public override SeriesData BeforeEvaluation(EvaluationSeriesContext context, SeriesData data)
        {
            return data;
        }

        public override SeriesData AfterEvaluation(EvaluationSeriesContext context, SeriesData data,
            EvaluationDetail<LdValue> detail)
        {
            LDObserve.TrackEvaluation(
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
