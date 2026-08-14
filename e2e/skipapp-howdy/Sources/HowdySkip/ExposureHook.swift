import Foundation

#if canImport(LaunchDarkly)
import LaunchDarkly
#endif
#if canImport(LaunchDarklyOtel)
import LaunchDarklyOtel
#endif

// MARK: - iOS

#if canImport(LaunchDarkly) && canImport(LaunchDarklyOtel)
/// Forwards per-key flag evaluations to OpenTelemetry.
///
/// This mirrors the exposure-hook example in `message-to-customer-flagsdk.md`, but
/// records a short `flag_exposure` span through `LDObserve` instead of adding a
/// Sentry breadcrumb. Registration wraps this hook in the SDK's `DedupingHook`.
final class ExposureHook: Hook {
    func metadata() -> Metadata {
        Metadata(name: "otel-exposure")
    }

    func afterEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: EvaluationSeriesData,
        evaluationDetail: LDEvaluationDetail<LDValue>
    ) -> EvaluationSeriesData {
        let value = evaluationDetail.value.toFoundation()
        LaunchDarklyObserve.recordSpan(
            "flag_exposure",
            properties: [
                "flag_key": seriesContext.flagKey,
                "variation": evaluationDetail.variationIndex ?? -1,
                "value": value
            ]
        ) {}
        return seriesData
    }
}
#endif

// MARK: - Android (transpiled / bridged)

#if SKIP
import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.IdentifySeriesContext

/// Kotlin-side counterpart of the iOS exposure hook.
///
/// `LDObserve` lives on the Kotlin side, so `recordExposure` starts and immediately
/// ends the same `flag_exposure` span produced by `LaunchDarklyObserve.recordSpan`
/// on iOS. Deduplication is provided by Android SDK 5.14's `DedupingHook`; this
/// hook deliberately keeps no exposure cache or identify-reset logic of its own.
// SKIP @nobridge
public class ExposureHook: Hook {
    public init() {
        super.init("otel-exposure")
    }

    public override func afterEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: kotlin.collections.MutableMap<String, Any>,
        evaluationDetail: EvaluationDetail<LDValue>
    ) -> kotlin.collections.MutableMap<String, Any> {
        let value = evaluationDetail.getValue().toJsonString()
        LaunchDarklyObserveAndroid.recordExposure(
            flagKey: seriesContext.flagKey,
            variation: evaluationDetail.getVariationIndex(),
            value: value
        )
        return seriesData
    }
}
#endif
