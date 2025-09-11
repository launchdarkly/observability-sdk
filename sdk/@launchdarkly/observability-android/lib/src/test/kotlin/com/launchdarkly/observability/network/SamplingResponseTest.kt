package com.launchdarkly.observability.network

import com.launchdarkly.observability.sampling.MatchConfig
import com.launchdarkly.observability.sampling.AttributeMatchConfig
import com.launchdarkly.observability.sampling.SpanSamplingConfig
import com.launchdarkly.observability.sampling.LogSamplingConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class SamplingResponseTest {

    @Nested
    @DisplayName("SamplingResponse mapping")
    inner class SamplingResponseMappingTests {

        @Test
        fun `should map to null when sampling is null`() {
            val response = SamplingResponse(sampling = null)
            val result = response.mapToEntity()
            assertNull(result)
        }

        @Test
        fun `should map to SamplingConfig when sampling is present`() {
            val response = SamplingResponse(
                sampling = SamplingConfigResponse(
                    spans = emptyList(),
                    logs = emptyList()
                )
            )
            val result = response.mapToEntity()
            assertNotNull(result)
            assertEquals(emptyList<SpanSamplingConfig>(), result?.spans)
            assertEquals(emptyList<LogSamplingConfig>(), result?.logs)
        }
    }

    @Nested
    @DisplayName("SamplingConfigResponse mapping")
    inner class SamplingConfigResponseMappingTests {

        @Test
        fun `should map empty lists when both spans and logs are null`() {
            val response = SamplingConfigResponse(spans = null, logs = null)
            val result = response.mapToEntity()
            assertEquals(emptyList<SpanSamplingConfig>(), result.spans)
            assertEquals(emptyList<LogSamplingConfig>(), result.logs)
        }

        @Test
        fun `should filter out null items from spans and logs lists`() {
            val response = SamplingConfigResponse(
                spans = listOf(
                    SpanSamplingConfigResponse(samplingRatio = 50),
                    null,
                    SpanSamplingConfigResponse(samplingRatio = 100)
                ),
                logs = listOf(
                    LogSamplingConfigResponse(samplingRatio = 25),
                    null
                )
            )
            val result = response.mapToEntity()
            assertEquals(2, result.spans.size)
            assertEquals(1, result.logs.size)
        }

        @Test
        fun `should filter out items that map to null`() {
            val response = SamplingConfigResponse(
                spans = listOf(
                    SpanSamplingConfigResponse(samplingRatio = 50),
                    SpanSamplingConfigResponse(samplingRatio = null) // This will map to null
                ),
                logs = listOf(
                    LogSamplingConfigResponse(samplingRatio = null) // This will map to null
                )
            )
            val result = response.mapToEntity()
            assertEquals(1, result.spans.size)
            assertEquals(0, result.logs.size)
        }
    }

    @Nested
    @DisplayName("LogSamplingConfigResponse mapping")
    inner class LogSamplingConfigResponseMappingTests {

        @Test
        fun `should return null when samplingRatio is null`() {
            val response = LogSamplingConfigResponse(samplingRatio = null)
            val result = response.mapToEntity()
            assertNull(result)
        }

        @Test
        fun `should map all fields correctly`() {
            val response = LogSamplingConfigResponse(
                message = MatchConfigResponse(matchValue = "error"),
                severityText = MatchConfigResponse(regexValue = ".*ERROR.*"),
                attributes = listOf(
                    AttributeMatchConfigResponse(
                        key = MatchConfigResponse(matchValue = "service"),
                        attribute = MatchConfigResponse(matchValue = "api")
                    )
                ),
                samplingRatio = 75
            )
            val result = response.mapToEntity()
            assertNotNull(result)
            assertEquals(75, result?.samplingRatio)
            assertEquals(MatchConfig.Value("error"), result?.message)
            assertEquals(MatchConfig.Regex(".*ERROR.*"), result?.severityText)
            assertEquals(1, result?.attributes?.size)
            assertEquals(MatchConfig.Value("service"), result?.attributes?.get(0)?.key)
            assertEquals(MatchConfig.Value("api"), result?.attributes?.get(0)?.attribute)
        }

        @Test
        fun `should filter null attributes`() {
            val response = LogSamplingConfigResponse(
                attributes = listOf(
                    AttributeMatchConfigResponse(
                        key = MatchConfigResponse(matchValue = "service"),
                        attribute = MatchConfigResponse(matchValue = "api")
                    ),
                    null
                ),
                samplingRatio = 50
            )
            val result = response.mapToEntity()
            assertEquals(1, result?.attributes?.size)
        }
    }

    @Nested
    @DisplayName("SpanSamplingConfigResponse mapping")
    inner class SpanSamplingConfigResponseMappingTests {

        @Test
        fun `should return null when samplingRatio is null`() {
            val response = SpanSamplingConfigResponse(samplingRatio = null)
            val result = response.mapToEntity()
            assertNull(result)
        }

        @Test
        fun `should map all fields correctly`() {
            val response = SpanSamplingConfigResponse(
                name = MatchConfigResponse(regexValue = ".*http.*"),
                attributes = listOf(
                    AttributeMatchConfigResponse(
                        key = MatchConfigResponse(matchValue = "method"),
                        attribute = MatchConfigResponse(matchValue = "GET")
                    )
                ),
                events = listOf(
                    SpanEventMatchConfigResponse(
                        name = MatchConfigResponse(matchValue = "exception")
                    )
                ),
                samplingRatio = 25
            )
            val result = response.mapToEntity()
            assertNotNull(result)
            assertEquals(25, result?.samplingRatio)
            assertEquals(MatchConfig.Regex(".*http.*"), result?.name)
            assertEquals(1, result?.attributes?.size)
            assertEquals(1, result?.attributes?.size)
            assertEquals(MatchConfig.Value("method"), result?.attributes?.get(0)?.key)
            assertEquals(MatchConfig.Value("GET"), result?.attributes?.get(0)?.attribute)
            assertEquals(MatchConfig.Value("exception"), result?.events?.get(0)?.name)
        }

        @Test
        fun `should filter null attributes and events`() {
            val response = SpanSamplingConfigResponse(
                attributes = listOf(
                    AttributeMatchConfigResponse(
                        key = MatchConfigResponse(matchValue = "key"),
                        attribute = MatchConfigResponse(matchValue = "value")
                    ),
                    null
                ),
                events = listOf(
                    SpanEventMatchConfigResponse(),
                    null
                ),
                samplingRatio = 50
            )
            val result = response.mapToEntity()
            assertEquals(1, result?.attributes?.size)
            assertEquals(1, result?.events?.size)
        }
    }

    @Nested
    @DisplayName("SpanEventMatchConfigResponse mapping")
    inner class SpanEventMatchConfigResponseMappingTests {

        @Test
        fun `should map with all null fields`() {
            val response = SpanEventMatchConfigResponse()
            val result = response.mapToEntity()
            assertNull(result.name)
            assertEquals(emptyList<AttributeMatchConfig>(), result.attributes)
        }

        @Test
        fun `should map all fields correctly`() {
            val response = SpanEventMatchConfigResponse(
                name = MatchConfigResponse(matchValue = "exception"),
                attributes = listOf(
                    AttributeMatchConfigResponse(
                        key = MatchConfigResponse(matchValue = "exception.type"),
                        attribute = MatchConfigResponse(regexValue = ".*Exception")
                    )
                )
            )
            val result = response.mapToEntity()
            assertEquals(MatchConfig.Value("exception"), result.name)
            assertEquals(1, result.attributes.size)
            assertEquals(MatchConfig.Value("exception.type"), result.attributes[0].key)
            assertEquals(MatchConfig.Regex(".*Exception"), result.attributes[0].attribute)
        }

        @Test
        fun `should filter null attributes`() {
            val response = SpanEventMatchConfigResponse(
                attributes = listOf(
                    AttributeMatchConfigResponse(
                        key = MatchConfigResponse(matchValue = "key"),
                        attribute = MatchConfigResponse(matchValue = "value")
                    ),
                    null
                )
            )
            val result = response.mapToEntity()
            assertEquals(1, result.attributes.size)
        }
    }

    @Nested
    @DisplayName("AttributeMatchConfigResponse mapping")
    inner class AttributeMatchConfigResponseMappingTests {

        @Test
        fun `should return null when key mapping fails`() {
            val response = AttributeMatchConfigResponse(
                key = MatchConfigResponse(), // Empty - will map to null
                attribute = MatchConfigResponse(matchValue = "value")
            )
            val result = response.mapToEntity()
            assertNull(result)
        }

        @Test
        fun `should return null when attribute mapping fails`() {
            val response = AttributeMatchConfigResponse(
                key = MatchConfigResponse(matchValue = "key"),
                attribute = MatchConfigResponse() // Empty - will map to null
            )
            val result = response.mapToEntity()
            assertNull(result)
        }

        @Test
        fun `should map successfully with both fields valid`() {
            val response = AttributeMatchConfigResponse(
                key = MatchConfigResponse(matchValue = "service.name"),
                attribute = MatchConfigResponse(regexValue = ".*api.*")
            )
            val result = response.mapToEntity()
            assertNotNull(result)
            assertEquals(MatchConfig.Value("service.name"), result?.key)
            assertEquals(MatchConfig.Regex(".*api.*"), result?.attribute)
        }
    }

    @Nested
    @DisplayName("MatchConfigResponse mapping")
    inner class MatchConfigResponseMappingTests {

        @Test
        fun `should return null when both values are null`() {
            val response = MatchConfigResponse(regexValue = null, matchValue = null)
            val result = response.mapToEntity()
            assertNull(result)
        }

        @Test
        fun `should return Regex when regexValue is present`() {
            val response = MatchConfigResponse(regexValue = ".*test.*", matchValue = "ignore")
            val result = response.mapToEntity()
            assertNotNull(result)
            assertTrue(result is MatchConfig.Regex)
            assertEquals(".*test.*", (result as MatchConfig.Regex).pattern)
        }

        @Test
        fun `should return Value when only matchValue is present`() {
            val response = MatchConfigResponse(regexValue = null, matchValue = "exact")
            val result = response.mapToEntity()
            assertNotNull(result)
            assertTrue(result is MatchConfig.Value)
            assertEquals("exact", (result as MatchConfig.Value).value)
        }
    }
}
