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
    val attributes: List<AttributeMatchConfig> = emptyList()
)

data class SpanSamplingConfig(
    val name: MatchConfig? = null,
    val attributes: List<AttributeMatchConfig> = emptyList(),
    val events: List<SpanEventMatchConfig> = emptyList(),
    val samplingRatio: Int
)

data class LogSamplingConfig(
    val message: MatchConfig? = null,
    val severityText: MatchConfig? = null,
    val attributes: List<AttributeMatchConfig> = emptyList(),
    val samplingRatio: Int
)

data class SamplingConfig(
    val spans: List<SpanSamplingConfig> = emptyList(),
    val logs: List<LogSamplingConfig> = emptyList()
)
