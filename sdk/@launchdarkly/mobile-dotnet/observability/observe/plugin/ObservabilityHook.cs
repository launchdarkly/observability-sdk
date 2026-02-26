using System.Collections.Generic;
using System.Collections.Immutable;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client.Hooks;
using LaunchDarkly.SessionReplay;

namespace LaunchDarkly.Observability
{
    using SeriesData = ImmutableDictionary<string, object>;

    /// <summary>
    /// Hook that delegates evaluation and identify calls to the native
    /// ObservabilityHookImplementation (via NativeHookProxy) on iOS,
    /// and is a no-op on other platforms.
    /// </summary>
    internal sealed class ObservabilityHook : Hook
    {
        private readonly NativeObserve _nativeObserve;
        private IList<Hook>? _nativeHooks;

        internal ObservabilityHook(NativeObserve nativeObserve)
            : base("LaunchDarkly.Observability")
        {
            _nativeObserve = nativeObserve;
        }

        private IList<Hook> NativeHooks => _nativeHooks ??= _nativeObserve.GetNativeHooks();

        public override SeriesData BeforeEvaluation(EvaluationSeriesContext context, SeriesData data)
        {
            foreach (var hook in NativeHooks)
            {
                data = hook.BeforeEvaluation(context, data);
            }
            return data;
        }

        public override SeriesData AfterEvaluation(EvaluationSeriesContext context, SeriesData data,
            EvaluationDetail<LdValue> detail)
        {
            foreach (var hook in NativeHooks)
            {
                data = hook.AfterEvaluation(context, data, detail);
            }
            return data;
        }

        public override SeriesData BeforeIdentify(IdentifySeriesContext context, SeriesData data)
        {
            foreach (var hook in NativeHooks)
            {
                data = hook.BeforeIdentify(context, data);
            }
            return data;
        }

        public override SeriesData AfterIdentify(IdentifySeriesContext context, SeriesData data,
            IdentifySeriesResult result)
        {
            foreach (var hook in NativeHooks)
            {
                data = hook.AfterIdentify(context, data, result);
            }
            return data;
        }
    }
}
