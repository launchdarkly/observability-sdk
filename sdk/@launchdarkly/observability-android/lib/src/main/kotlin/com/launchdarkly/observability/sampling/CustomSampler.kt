package com.launchdarkly.observability.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import kotlin.math.floor
import kotlin.math.truncate
import kotlin.random.Random

typealias RegexCache = MutableMap<String, Regex>

/**
 * Custom sampler that uses a sampling configuration to determine if a span should be sampled.
 */
class CustomSampler(
    private val sampler: (Int) -> Boolean = ::defaultSampler
) : ExportSampler {
    private val regexCache: RegexCache = mutableMapOf()
    private var config: SamplingConfig? = null

    companion object {
        private const val ATTR_SAMPLING_RATIO = "launchdarkly.sampling.ratio"
    }

    override fun setConfig(config: SamplingConfig?) {
        this.config = config
    }

    override fun isSamplingEnabled(): Boolean {
        return config?.logs?.isNotEmpty() == true || config?.spans?.isNotEmpty() == true
    }

    /**
     * Check if a value matches a match config.
     */
    private fun matchesValue(
        matchConfig: MatchConfig?,
        value: Any?
    ): Boolean {
        if (matchConfig == null) {
            return false
        }

        return when (matchConfig) {
            is MatchConfig.Value -> matchConfig.value == value
            is MatchConfig.Regex -> {
                if (value !is String) {
                    false
                } else {
                    val regexPattern = matchConfig.pattern
                    val regex = regexCache.getOrPut(regexPattern) { Regex(matchConfig.pattern) }
                    regex.matches(value)
                }
            }
        }
    }

    /**
     * Check if the attributes match the attribute configs.
     */
    private fun matchesAttributes(
        attributeConfigs: List<AttributeMatchConfig>?,
        attributes: Attributes?
    ): Boolean {
        if (attributeConfigs.isNullOrEmpty()) {
            return true
        }

        // No attributes, so they cannot match.
        if (attributes == null) {
            return false
        }

        return attributeConfigs.all { config -> // Check if ALL configurations are met
            attributes.asMap().any { (key, value) -> // Check if ANY attribute matches the current config
                matchesValue(config.key, key) && matchesValue(config.attribute, value)
            }
        }
    }

    private fun matchEvent(
        eventConfig: SpanEventMatchConfig,
        event: EventData
    ): Boolean {
        if (eventConfig.name != null) {
            // Match by event name
            if (!matchesValue(eventConfig.name, event.name)) {
                return false
            }
        }

        // Match by event attributes if specified
        if (eventConfig.attributes != null) {
            if (!matchesAttributes(eventConfig.attributes, event.attributes)) {
                return false
            }
        }
        return true
    }

    private fun matchesEvents(
        eventConfigs: List<SpanEventMatchConfig>?,
        events: List<EventData>
    ): Boolean {
        if (eventConfigs.isNullOrEmpty()) {
            return true
        }

        if (events.isEmpty()) {
            return false
        }

        return eventConfigs.all { eventConfig ->
            events.any { event -> matchEvent(eventConfig, event) }
        }
    }

    /**
     * Attempts to match the span to the config. The span will match only if all defined conditions are met.
     *
     * @param config The config to match against.
     * @param span The span to match.
     * @returns True if the span matches the config, false otherwise.
     */
    private fun matchesSpanConfig(
        config: SpanSamplingConfig,
        span: SpanData
    ): Boolean {
        // Check span name if it's defined in the config
        if (config.name != null) {
            if (!matchesValue(config.name, span.name)) {
                return false
            }
        }

        if (!matchesAttributes(config.attributes, span.attributes)) {
            return false
        }

        if (!matchesEvents(config.events, span.events)) {
            return false
        }

        // If we reach here, all conditions were met
        return true
    }

    private fun matchesLogConfig(
        config: LogSamplingConfig,
        record: LogRecordData
    ): Boolean {
        if (config.severityText != null) {
            val severityText = record.severity?.name
            if (severityText != null && !matchesValue(config.severityText, severityText)) {
                return false
            }
        }

        if (config.message != null) {
            val message = record.bodyValue?.asString()
            if (message != null && !matchesValue(config.message, message)) {
                return false
            }
        }

        return matchesAttributes(config.attributes, record.attributes)
    }

    /**
     * Sample a span based on the sampling configuration.
     *
     * @param span The span to sample.
     * @returns The sampling result.
     */
    override fun sampleSpan(span: SpanData): SamplingResult {
        config?.spans?.let { spans ->
            for (spanConfig in spans) {
                if (matchesSpanConfig(spanConfig, span)) {
                    return SamplingResult(
                        sample = sampler(spanConfig.samplingRatio),
                        attributes = Attributes.builder()
                            .put(ATTR_SAMPLING_RATIO, spanConfig.samplingRatio.toLong())
                            .build()
                    )
                }
            }
        }
        // Didn't match any sampling config, or there were no configs, so we sample it.
        return SamplingResult(sample = true)
    }

    /**
     * Sample a log based on the sampling configuration.
     *
     * @param log The log to sample.
     * @returns The sampling result.
     */
    override fun sampleLog(log: LogRecordData): SamplingResult {
        config?.logs?.let { logs ->
            for (logConfig in logs) {
                if (matchesLogConfig(logConfig, log)) {
                    return SamplingResult(
                        sample = sampler(logConfig.samplingRatio),
                        attributes = Attributes.builder()
                            .put(ATTR_SAMPLING_RATIO, logConfig.samplingRatio.toLong())
                            .build()
                    )
                }
            }
        }

        // Didn't match any sampling config, or there were no configs, so we sample it.
        return SamplingResult(sample = true)
    }
}

/**
 * Determine if an item should be sampled based on the sampling ratio.
 *
 * This function is not used for any purpose requiring cryptographic security.
 *
 * @param ratio The sampling ratio.
 * @returns True if the item should be sampled, false otherwise.
 */
fun defaultSampler(ratio: Int): Boolean {
    val truncated = truncate(ratio.toDouble()).toInt()

    // A ratio of 1 means 1 in 1. So that will always sample. No need to draw a random number.
    if (truncated == 1) {
        return true
    }

    // A ratio of 0 means 0 in 1. So that will never sample.
    if (truncated == 0) {
        return false
    }

    // Random.nextDouble() * truncated would return 0, 1, ... (ratio - 1)
    // Checking for any number in the range will have approximately a 1 in X chance. So we check for 0 as it is part of any range.
    return floor(Random.nextDouble() * truncated).toInt() == 0
}