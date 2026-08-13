My message:
1. We are providing LaunchDarklyOtel for iOS and Android. It is a simplified version of LaunchDarklyObservability with all automatic instrumentation removed, so it will not conflict with Sentry's instrumentation. It provides full OpenTelemetry functionality, including error reporting through `recordError`.
The OpenTelemetry library handles buffering internally, so events are sent in batches.

You do not need to use `track` to report errors—just use `LDObserve.recordError`. This API is designed for telemetry, uses its own OpenTelemetry buffering and batching, and keeps errors out of the SDK's `track` and feature-event pipeline.

We are also providing a SKIP end-to-end sample that demonstrates hook and flag integration.

There is no bulk or out-of-band exposure API: exposures come from per-key variation calls, and those are what run the evaluation hook series. To record "this context saw variation V of flag X" you register a hook that forwards the evaluation to your analytics, and evaluate the keys you care about. To keep the volume down when a flag is read on every render, wrap that hook in `DedupingHook`, which reports a flag once per result per window (default 10 minutes) and again as soon as the result changes or after an `identify`.

iOS:

```swift
final class ExposureHook: Hook {
    func metadata() -> Metadata { Metadata(name: "exposure-analytics") }

    func afterEvaluation(seriesContext: EvaluationSeriesContext,
                         seriesData: EvaluationSeriesData,
                         evaluationDetail: LDEvaluationDetail<LDValue>) -> EvaluationSeriesData {
        analytics.record("flag_exposure", [
            "flag_key": seriesContext.flagKey,
            "variation": evaluationDetail.variationIndex as Any,
            "value": evaluationDetail.value
        ])
        return seriesData
    }
}

let config = LDConfig(mobileKey: mobileKey, autoEnvAttributes: .enabled)
config.hooks = [
    DedupingHook(ExposureHook())              // default 10 minute window
    // DedupingHook(ExposureHook(), window: 60)  // or your own window, in seconds
]
```

Android:

```java
class ExposureHook extends Hook {
    ExposureHook() {
        super("exposure-analytics");
    }

    @Override
    public Map<String, Object> afterEvaluation(EvaluationSeriesContext seriesContext,
                                               Map<String, Object> seriesData,
                                               EvaluationDetail<LDValue> evaluationDetail) {
        analytics.record("flag_exposure",
                seriesContext.getFlagKey(),
                evaluationDetail.getVariationIndex(),
                evaluationDetail.getValue());
        return seriesData;
    }
}

LDConfig config = new LDConfig.Builder(AutoEnvAttributes.Enabled)
        .mobileKey(mobileKey)
        .hooks(Components.hooks()
                .addHook(new DedupingHook(new ExposureHook())))       // default 10 minute window
                // .addHook(new DedupingHook(new ExposureHook(), 60_000)) // or your own window, in ms
        .build();
```

The closest thing to a bulk read is to evaluate the set of keys you care about once, after `identify`, and let the hook record them:

```swift
for key in trackedFlagKeys {
    _ = LDClient.get()?.boolVariation(forKey: key, defaultValue: false)
}
```

2. Evaluation hooks fire on every per-key variation call (`boolVariation`, `stringVariation`, etc.), including when the value is served from the local cache, which is the normal path for mobile client SDKs. There are no separate hook paths for live and cached evaluations.

They do **not** fire for the flag snapshot received after `identify` or client startup. That operation runs only the identify hook series (`beforeIdentify` / `afterIdentify`). Bulk reads such as `allFlags` / `allFlagsState` also do not run evaluation hooks.

3. Hook-emitted evaluation events, `track` events, and events reported through `LDObserve.recordError` use separate buffers.

4. Yes, the bulk event import API works with guarded rollouts: https://launchdarkly.com/docs/home/metrics/import-events

5.
- **Validation:** When you use `LDObserve.recordError`, validation is handled automatically. The error is formatted with its type, message, and stack trace before entering the OTel pipeline.
- **Flag evaluation deduplication:** The deduplication identity (`EvaluationExposureKey`) consists of the environment name, flag key, value, variation, flag version, and fully qualified context key. The default window is 10 minutes, but you can configure a different window through `DedupingHook` / `EvaluationExposureDeduper`.
- **Network traffic (LD OTel buffer):** The in-memory queue defaults to approximately 2.5 MB. New items are dropped if the queue exceeds that limit. The flush interval is approximately 1.5 seconds, and each batch carries up to approximately 30 KB. Failed exports are retried with exponential backoff (starting at 2 seconds, up to 60 seconds, with ±20% jitter).
