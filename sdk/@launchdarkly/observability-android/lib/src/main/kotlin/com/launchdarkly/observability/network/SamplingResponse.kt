package com.launchdarkly.observability.network

import com.launchdarkly.observability.sampling.MatchConfig
import com.launchdarkly.observability.sampling.AttributeMatchConfig
import com.launchdarkly.observability.sampling.SpanEventMatchConfig
import com.launchdarkly.observability.sampling.SpanSamplingConfig
import com.launchdarkly.observability.sampling.LogSamplingConfig
import com.launchdarkly.observability.sampling.SamplingConfig
import kotlinx.serialization.Serializable

/**
 * GraphQL response models for sampling configuration
 */
@Serializable
data class SamplingResponse(
    val sampling: SamplingConfigResponse?
) {
    fun mapToEntity(): SamplingConfig? = sampling?.mapToEntity()
}

@Serializable
data class SamplingConfigResponse(
    val spans: List<SpanSamplingConfigResponse?>? = null,
    val logs: List<LogSamplingConfigResponse?>? = null
) {
    fun mapToEntity(): SamplingConfig = SamplingConfig(
        spans = spans?.mapNotNull { it?.mapToEntity() } ?: emptyList(),
        logs = logs?.mapNotNull { it?.mapToEntity() } ?: emptyList()
    )
}

@Serializable
data class LogSamplingConfigResponse(
    val message: MatchConfigResponse? = null,
    val severityText: MatchConfigResponse? = null,
    val attributes: List<AttributeMatchConfigResponse?>? = null,
    val samplingRatio: Int?
) {
    fun mapToEntity(): LogSamplingConfig? {
        return LogSamplingConfig(
            message = message?.mapToEntity(),
            severityText = severityText?.mapToEntity(),
            attributes = attributes?.mapNotNull { it?.mapToEntity() } ?: emptyList(),
            samplingRatio = samplingRatio ?: return null // If samplingRatio is null, mapping result will return null
        )
    }
}

@Serializable
data class SpanSamplingConfigResponse(
    val name: MatchConfigResponse? = null,
    val attributes: List<AttributeMatchConfigResponse?>? = null,
    val events: List<SpanEventMatchConfigResponse?>? = null,
    val samplingRatio: Int?
) {
    fun mapToEntity(): SpanSamplingConfig? {
        return SpanSamplingConfig(
            name = name?.mapToEntity(),
            attributes = attributes?.mapNotNull { it?.mapToEntity() } ?: emptyList(),
            events = events?.mapNotNull { it?.mapToEntity() } ?: emptyList(),
            samplingRatio = samplingRatio ?: return null // If samplingRatio is null, mapping result will return null
        )
    }
}

@Serializable
data class SpanEventMatchConfigResponse(
    val name: MatchConfigResponse? = null,
    val attributes: List<AttributeMatchConfigResponse?>? = null
) {
    fun mapToEntity(): SpanEventMatchConfig = SpanEventMatchConfig(
        name = name?.mapToEntity(),
        attributes = attributes?.mapNotNull { it?.mapToEntity() } ?: emptyList()
    )
}

@Serializable
data class AttributeMatchConfigResponse(
    val key: MatchConfigResponse,
    val attribute: MatchConfigResponse
) {
    fun mapToEntity(): AttributeMatchConfig? {
        return AttributeMatchConfig(
            key = key.mapToEntity() ?: return null,
            attribute = attribute.mapToEntity() ?: return null
        )
    }
}

@Serializable
data class MatchConfigResponse(
    val regexValue: String? = null,
    val matchValue: String? = null
) {
    fun mapToEntity(): MatchConfig? = when {
        regexValue != null -> MatchConfig.Regex(regexValue)
        matchValue != null -> MatchConfig.Value(matchValue)
        else -> null
    }
}
