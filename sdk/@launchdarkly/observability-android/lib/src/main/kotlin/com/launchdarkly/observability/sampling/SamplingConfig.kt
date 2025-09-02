package com.launchdarkly.observability.sampling

sealed class MatchConfig {
    data class Value(val value: Any) : MatchConfig()
    data class Regex(val pattern: String) : MatchConfig()
}

data class AttributeMatchConfig(
    val key: MatchConfig,
    val attribute: MatchConfig
)

data class SpanEventMatchConfig(
    val name: MatchConfig? = null,
    val attributes: List<AttributeMatchConfig>? = null
)

data class SpanSamplingConfig(
    val name: MatchConfig? = null,
    val attributes: List<AttributeMatchConfig>? = null,
    val events: List<SpanEventMatchConfig>? = null,
    val samplingRatio: Int
)

data class LogSamplingConfig(
    val message: MatchConfig? = null,
    val severityText: MatchConfig? = null,
    val attributes: List<AttributeMatchConfig>? = null,
    val samplingRatio: Int
)

data class SamplingConfig(
    val spans: List<SpanSamplingConfig>? = null,
    val logs: List<LogSamplingConfig>? = null
)
