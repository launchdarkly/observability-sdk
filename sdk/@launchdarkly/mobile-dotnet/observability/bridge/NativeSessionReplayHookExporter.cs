#if IOS
using System.Collections.Immutable;
using Foundation;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client.Hooks;
using LDObserveMaciOS;

namespace LaunchDarkly.Observability
{
    using SeriesData = ImmutableDictionary<string, object>;

    internal sealed class NativeSessionReplayHookExporter
    {
        private readonly SessionReplayHookProxy _proxy;

        internal NativeSessionReplayHookExporter(SessionReplayHookProxy proxy)
        {
            _proxy = proxy;
        }

        internal SeriesData AfterIdentify(IdentifySeriesContext context, SeriesData data,
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
                    result.Status == IdentifySeriesResult.IdentifySeriesStatus.Completed);
          
            return data;
        }
    }
}
#elif ANDROID
using System.Collections.Immutable;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client.Hooks;
using LDObserveAndroid;

namespace LaunchDarkly.Observability
{
    using SeriesData = ImmutableDictionary<string, object>;

    internal sealed class NativeSessionReplayHookExporter
    {
        private readonly RealSessionReplayHookProxy _proxy;

        internal NativeSessionReplayHookExporter(RealSessionReplayHookProxy proxy)
        {
            _proxy = proxy;
        }

        internal SeriesData AfterIdentify(IdentifySeriesContext context, SeriesData data,
            IdentifySeriesResult result)
        {
            var contextKeys = new System.Collections.Generic.Dictionary<string, string>();
            if (context.Context.Multiple)
            {
                foreach (var individual in context.Context.MultiKindContexts)
                {
                    contextKeys[individual.Kind.Value] = individual.Key;
                }
            }
            else
            {
                contextKeys[context.Context.Kind.Value] = context.Context.Key;
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
