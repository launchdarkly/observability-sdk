package com.launchdarkly.observability.plugin

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.utils.BoundedMap
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.LDValueType
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.json.JSONObject

/**
 * Pure data-sending logic for observability hook tracing.
 *
 * Manages span lifecycle (start/end) and identify logging.
 * Takes only simple JVM types — no Hook interface, no SDK-specific types.
 * Both [ObservabilityHook] (native Android SDK) and [com.launchdarkly.observability.bridge.ObservabilityHookProxy] (C# bridge)
 * delegate here so the tracing logic is written exactly once.
 */
internal class ObservabilityHookExporter(
    private val withSpans: Boolean,
    private val withValue: Boolean,
    private val tracerProvider: (() -> Tracer?),
    private val logger: ObserveLogger,
    private val contextFriendlyName: String? = null,
    maxInFlightSpans: Int = 1024
) : ObservabilityHookExporting {
    private val spans = BoundedMap<String, Span>(maxInFlightSpans)

    /**
     * LD context-key attributes cached from the most recent [afterIdentify] call. Spread as
     * bare top-level keys (e.g. `user = "alice"`, `org = "team-a"`) on the `launchdarkly.track`
     * span — matches the JS reference (see `sdk/highlight-run/src/plugins/observe.ts:212`).
     *
     * `@Volatile` so any thread that subsequently calls [track] sees the most recent identify.
     */
    @Volatile
    private var ldContextKeyAttributes: Attributes = Attributes.empty()

    /**
     * SDK metadata attributes (`telemetry.sdk.name/version`, `feature_flag.set.id`,
     * `feature_flag.provider.name`, `launchdarkly.application.id/version`). Populated by the
     * [com.launchdarkly.observability.plugin.Observability] plugin on `onPluginsReady` once it
     * has the LD client's `EnvironmentMetadata`. Spread on the `launchdarkly.track` span — matches
     * the JS reference `metaAttrs` (see `sdk/highlight-run/src/plugins/observe.ts:99-113`).
     */
    @Volatile
    private var metaAttributes: Attributes = Attributes.empty()

    private fun getTracer(): Tracer {
        return tracerProvider.invoke() ?: GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION_NAME)
    }

    /**
     * Publishes the SDK metadata attributes that should be spread on every `launchdarkly.track`
     * span. Called once by the [com.launchdarkly.observability.plugin.Observability] plugin
     * from `onPluginsReady` after the LD client's `EnvironmentMetadata` is available.
     */
    fun setMetaAttributes(attrs: Attributes) {
        metaAttributes = attrs
    }

    /**
     * Emits a `launchdarkly.track` span — the shared code path for both the
     * [ObservabilityHook.afterTrack] hook callback (i.e. when application code calls
     * `LDClient.track(...)`) and direct [com.launchdarkly.observability.sdk.LDObserve.track]
     * calls.
     *
     * Wrapped in `try/catch(Throwable)`: any failure is logged at debug and swallowed so that
     * the LD client's `track()` always completes normally and ad-hoc o11y telemetry never
     * surfaces an unexpected exception to the application.
     *
     * The `productAnalyticsApi.trackEvents` gate is owned by the caller — see
     * [com.launchdarkly.observability.client.ObservabilityService.track] — so this exporter
     * stays decoupled from the options struct.
     *
     * @param key         the track event key, written as the `key` attribute on the span
     * @param data        optional structured payload; when this resolves to an [LDValueType.OBJECT],
     *                    its property names are spread as top-level attributes. Non-object / null
     *                    payloads are skipped (no spread, no failure).
     * @param metricValue optional numeric value, written as the `value` attribute on the span.
     */
    fun track(
        key: String,
        data: LDValue?,
        metricValue: Double?,
    ) {
        try {
            val tracer = getTracer()
            val attrBuilder = Attributes.builder()

            // Bare context-key spread (e.g. `user = "alice"`), matching the JS reference.
            attrBuilder.putAll(ldContextKeyAttributes)

            // SDK metadata (`telemetry.sdk.*`, `feature_flag.*`, `launchdarkly.application.*`).
            attrBuilder.putAll(metaAttributes)

            attrBuilder.put(AttributeKey.stringKey(ATTR_KEY), key)

            if (metricValue != null) {
                attrBuilder.put(AttributeKey.doubleKey(ATTR_VALUE), metricValue)
            }

            // Spread `data` properties when it resolves to an OBJECT. Non-object / null
            // payloads are skipped — matches the JS `typeof hookContext.data === 'object'` guard.
            spreadLDValueObject(attrBuilder, data)

            tracer.spanBuilder(LD_TRACK_SPAN_NAME)
                .setAllAttributes(attrBuilder.build())
                .startSpan()
                .end()
        } catch (t: Throwable) {
            // Swallow: hook safety contract — the LD client's track() must always return normally.
            logger.debug("LDObserve.track failed: ${t.message}")
        }
    }

    /**
     * Spreads each property of an [LDValueType.OBJECT] payload as a top-level attribute on the
     * span, picking a typed `AttributeKey` per element so the attribute appears with its natural
     * JSON type at the backend (instead of being stringified). Arrays / scalars / null / non-OBJECT
     * payloads are skipped — matches the JS reference's `typeof === 'object'` guard.
     */
    private fun spreadLDValueObject(builder: AttributesBuilder, data: LDValue?) {
        if (data == null || data.isNull) return
        if (data.type != LDValueType.OBJECT) return

        for (propName in data.keys()) {
            val v = data.get(propName)
            when (v.type) {
                LDValueType.STRING -> builder.put(AttributeKey.stringKey(propName), v.stringValue())
                LDValueType.BOOLEAN -> builder.put(AttributeKey.booleanKey(propName), v.booleanValue())
                LDValueType.NUMBER -> builder.put(AttributeKey.doubleKey(propName), v.doubleValue())
                // Nested arrays / objects / null are serialized as their JSON representation so
                // the attribute is still useful at the backend without losing information.
                LDValueType.ARRAY,
                LDValueType.OBJECT,
                LDValueType.NULL -> builder.put(AttributeKey.stringKey(propName), v.toJsonString())
            }
        }
    }

    // -- Evaluation --

    override fun beforeEvaluation(evaluationId: String, flagKey: String, contextKey: String) {
        if (!withSpans) return

        val tracer = getTracer()
        val span = tracer.spanBuilder(FEATURE_FLAG_SPAN_NAME)
            .setAllAttributes(
                Attributes.builder()
                    .put(SEMCONV_FEATURE_FLAG_KEY, flagKey)
                    .put(SEMCONV_FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)
                    .put(SEMCONV_FEATURE_FLAG_CONTEXT_ID, contextKey)
                    .build()
            )
            .startSpan()

        val evicted = spans.put(evaluationId, span)
        evicted?.end()
    }

    override fun afterEvaluation(
        evaluationId: String,
        flagKey: String,
        contextKey: String,
        valueJson: String?,
        variationIndex: Int?,
        inExperiment: Boolean?
    ) {
        val span = spans.remove(evaluationId) ?: return

        val attrBuilder = Attributes.builder()
        attrBuilder.put(SEMCONV_FEATURE_FLAG_KEY, flagKey)
        attrBuilder.put(SEMCONV_FEATURE_FLAG_PROVIDER_NAME, PROVIDER_NAME)
        attrBuilder.put(SEMCONV_FEATURE_FLAG_CONTEXT_ID, contextKey)

        inExperiment?.let {
            attrBuilder.put(CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT, it)
        }

        if (withValue && valueJson != null) {
            attrBuilder.put(SEMCONV_FEATURE_FLAG_RESULT_VALUE, valueJson)
        }

        variationIndex?.let {
            attrBuilder.put(CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX, it.toLong())
        }

        span.addEvent(FEATURE_FLAG_EVENT_NAME, attrBuilder.build())
        span.end()
    }

    fun afterEvaluation(
        evaluationId: String,
        flagKey: String,
        contextKey: String,
        valueJson: String,
        variationIndex: Int,
        reasonJson: String?
    ) {
        val normalizedIndex = if (variationIndex >= 0) variationIndex else null
        afterEvaluation(evaluationId, flagKey, contextKey, valueJson, normalizedIndex, parseInExperiment(reasonJson))
    }

    private fun parseInExperiment(reasonJson: String?): Boolean? {
        if (reasonJson == null) return null
        return try {
            val json = JSONObject(reasonJson)
            if (json.has("inExperiment")) json.getBoolean("inExperiment") else null
        } catch (_: Exception) {
            null
        }
    }

    // -- Identify --

    override fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        if (!completed) return

        // Cache the bare-key spread for the launchdarkly.track span. We keep this assignment
        // outside the log-emission block on purpose: even if log emission is disabled (or fails)
        // we still want subsequent track spans to carry the right context-key attribution.
        val contextKeyAttrs = Attributes.builder().apply {
            for ((k, v) in contextKeys) {
                put(AttributeKey.stringKey(k), v)
            }
        }.build()
        ldContextKeyAttributes = contextKeyAttrs

        val attrBuilder = Attributes.builder()
        attrBuilder.putAll(contextKeyAttrs)
        attrBuilder.put(AttributeKey.stringKey("key"), contextFriendlyName ?: canonicalKey)
        attrBuilder.put(AttributeKey.stringKey("canonicalKey"), canonicalKey)
        attrBuilder.put(AttributeKey.stringKey(IDENTIFY_RESULT_STATUS), "completed")

        LDObserve.recordLog("LD.identify", Severity.INFO, attrBuilder.build())
    }

    companion object {
        const val PROVIDER_NAME = "LaunchDarkly"
        const val INSTRUMENTATION_NAME = "com.launchdarkly.observability"
        const val FEATURE_FLAG_SPAN_NAME = "evaluation"
        const val FEATURE_FLAG_EVENT_NAME = "feature_flag"
        const val SEMCONV_FEATURE_FLAG_KEY = "feature_flag.key"
        const val SEMCONV_FEATURE_FLAG_PROVIDER_NAME = "feature_flag.provider.name"
        const val SEMCONV_FEATURE_FLAG_CONTEXT_ID = "feature_flag.context.id"
        const val SEMCONV_FEATURE_FLAG_RESULT_VALUE = "feature_flag.result.value"
        const val CUSTOM_FEATURE_FLAG_RESULT_VARIATION_INDEX = "feature_flag.result.variationIndex"
        const val CUSTOM_FEATURE_FLAG_RESULT_REASON_IN_EXPERIMENT = "feature_flag.result.reason.inExperiment"
        const val IDENTIFY_RESULT_STATUS = "identify.result.status"
        const val DATA_KEY_EVAL_ID = "evaluationId"

        // launchdarkly.track span — emitted on every LDClient.track(...) call and on every
        // direct LDObserve.track(...) call. Matches the JS reference constant in
        // `sdk/highlight-run/src/integrations/launchdarkly/index.ts`.
        const val LD_TRACK_SPAN_NAME = "launchdarkly.track"
        const val ATTR_KEY = "key"
        const val ATTR_VALUE = "value"

        // SDK metadata attribute names; spread on the launchdarkly.track span via
        // [setMetaAttributes]. Match the JS constants in
        // `sdk/highlight-run/src/integrations/launchdarkly/index.ts`.
        const val ATTR_TELEMETRY_SDK_NAME = "telemetry.sdk.name"
        const val ATTR_TELEMETRY_SDK_VERSION = "telemetry.sdk.version"
        const val ATTR_FEATURE_FLAG_SET_ID = "feature_flag.set.id"
        const val ATTR_FEATURE_FLAG_PROVIDER_NAME = "feature_flag.provider.name"
        const val ATTR_LD_APPLICATION_ID = "launchdarkly.application.id"
        const val ATTR_LD_APPLICATION_VERSION = "launchdarkly.application.version"
    }
}
