#if IOS
using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using Foundation;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client.Hooks;
using LDObserveMaciOS;

namespace LaunchDarkly.Observability
{
    using SeriesData = ImmutableDictionary<string, object>;

    /// <summary>
    /// A C# Hook that delegates to the native iOS ObservabilityHookImplementation.
    /// The implementation manages spans and logging internally on the Swift side.
    /// C# passes only primitives and Foundation types — no SDK-specific types
    /// (EvaluationSeriesContext, LDEvaluationDetail) need to be constructed natively.
    /// </summary>
    internal sealed class NativeHookProxy : Hook
    {
        private const string EvalIdKey = "__nativeEvalId";
        private const string IdentifyIdKey = "__nativeIdentifyId";

        private readonly ObservabilityHookProxy _proxy;

        internal NativeHookProxy(ObservabilityHookProxy proxy) : base("Observability")
        {
            _proxy = proxy;
        }

        public override SeriesData BeforeEvaluation(EvaluationSeriesContext context, SeriesData data)
        {
            var evalId = Guid.NewGuid().ToString();
            _proxy.BeforeEvaluation(evalId, context.FlagKey, context.Context.FullyQualifiedKey);
            return new SeriesDataBuilder(data).Set(EvalIdKey, evalId).Build();
        }

        public override SeriesData AfterEvaluation(EvaluationSeriesContext context, SeriesData data,
            EvaluationDetail<LdValue> detail)
        {
            var evalId = data.TryGetValue(EvalIdKey, out var id) ? (string)id : "";
            _proxy.AfterEvaluation(
                evalId,
                context.FlagKey,
                context.Context.FullyQualifiedKey,
                LdValueBridge.ToNative(detail.Value),
                detail.VariationIndex ?? -1,
                LdValueBridge.ReasonToNative(detail.Reason)
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
            var contextKeys = new NSMutableDictionary();
            if (context.Context.Multiple)
            {
                foreach (var individual in context.Context.MultiKindContexts)
                {
                    contextKeys.Add(new NSString(individual.Kind.Value), new NSString(individual.Key));
                }
            }
            else
            {
                contextKeys.Add(new NSString(context.Context.Kind.Value), new NSString(context.Context.Key));
            }
            _proxy.AfterIdentify(
                contextKeys,
                context.Context.FullyQualifiedKey,
                result.Status == IdentifySeriesResult.IdentifySeriesStatus.Completed
            );
            return data;
        }
    }
}
#endif
