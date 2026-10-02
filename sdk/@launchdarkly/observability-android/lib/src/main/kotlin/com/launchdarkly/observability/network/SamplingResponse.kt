package com.launchdarkly.observability.network

import com.launchdarkly.observability.sampling.MatchConfig
import com.launchdarkly.observability.sampling.AttributeMatchConfig
import com.launchdarkly.observability.sampling.SpanEventMatchConfig
import com.launchdarkly.observability.sampling.SpanSamplingConfig
import com.launchdarkly.observability.sampling.LogSamplingConfig
import com.launchdarkly.observability.sampling.SamplingConfig
import org.json.JSONObject

/**
 * GraphQL response models for sampling configuration
 */
data class SamplingResponse(
    val sampling: SamplingConfigResponse?
) {
    fun mapToEntity(): SamplingConfig? = sampling?.mapToEntity()

    internal companion object {
        fun fromJson(json: JSONObject) = SamplingResponse(
            sampling = json.objectOrNull("sampling", SamplingConfigResponse::fromJson)
        )
    }
}

data class SamplingConfigResponse(
    val spans: List<SpanSamplingConfigResponse?>? = null,
    val logs: List<LogSamplingConfigResponse?>? = null
) {
    fun mapToEntity(): SamplingConfig = SamplingConfig(
        spans = spans?.mapNotNull { it?.mapToEntity() } ?: emptyList(),
        logs = logs?.mapNotNull { it?.mapToEntity() } ?: emptyList()
    )

    internal companion object {
        fun fromJson(json: JSONObject) = SamplingConfigResponse(
            spans = json.objectListOrNull("spans", SpanSamplingConfigResponse::fromJson),
            logs = json.objectListOrNull("logs", LogSamplingConfigResponse::fromJson)
        )
    }
}

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

    internal companion object {
        fun fromJson(json: JSONObject) = LogSamplingConfigResponse(
            message = json.objectOrNull("message", MatchConfigResponse::fromJson),
            severityText = json.objectOrNull("severityText", MatchConfigResponse::fromJson),
            attributes = json.objectListOrNull("attributes", AttributeMatchConfigResponse::fromJson),
            samplingRatio = json.intOrNull("samplingRatio")
        )
    }
}

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

    internal companion object {
        fun fromJson(json: JSONObject) = SpanSamplingConfigResponse(
            name = json.objectOrNull("name", MatchConfigResponse::fromJson),
            attributes = json.objectListOrNull("attributes", AttributeMatchConfigResponse::fromJson),
            events = json.objectListOrNull("events", SpanEventMatchConfigResponse::fromJson),
            samplingRatio = json.intOrNull("samplingRatio")
        )
    }
}

data class SpanEventMatchConfigResponse(
    val name: MatchConfigResponse? = null,
    val attributes: List<AttributeMatchConfigResponse?>? = null
) {
    fun mapToEntity(): SpanEventMatchConfig = SpanEventMatchConfig(
        name = name?.mapToEntity(),
        attributes = attributes?.mapNotNull { it?.mapToEntity() } ?: emptyList()
    )

    internal companion object {
        fun fromJson(json: JSONObject) = SpanEventMatchConfigResponse(
            name = json.objectOrNull("name", MatchConfigResponse::fromJson),
            attributes = json.objectListOrNull("attributes", AttributeMatchConfigResponse::fromJson)
        )
    }
}

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

    internal companion object {
        fun fromJson(json: JSONObject) = AttributeMatchConfigResponse(
            key = MatchConfigResponse.fromJson(json.getJSONObject("key")),
            attribute = MatchConfigResponse.fromJson(json.getJSONObject("attribute"))
        )
    }
}

data class MatchConfigResponse(
    val regexValue: String? = null,
    val matchValue: String? = null
) {
    fun mapToEntity(): MatchConfig? = when {
        regexValue != null -> MatchConfig.Regex(regexValue)
        matchValue != null -> MatchConfig.Value(matchValue)
        else -> null
    }

    internal companion object {
        fun fromJson(json: JSONObject) = MatchConfigResponse(
            regexValue = json.stringOrNull("regexValue"),
            matchValue = json.stringOrNull("matchValue")
        )
    }
}
