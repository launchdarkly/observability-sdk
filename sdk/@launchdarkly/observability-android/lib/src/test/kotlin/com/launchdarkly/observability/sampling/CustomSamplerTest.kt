package com.launchdarkly.observability.sampling

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.random.Random

class CustomSamplerTest {

    private lateinit var customSampler: CustomSampler
    private val mockSamplerFunction: (Int) -> Boolean = mockk()


    @BeforeEach
    fun setup() {
        customSampler = CustomSampler(mockSamplerFunction)
    }

    @Nested
    @DisplayName("Configuration Tests")
    inner class ConfigurationTests {

        @Test
        fun `should return true for isSamplingEnabled when config has spans and logs`() {
            val config = SamplingConfig(
                spans = listOf(SpanSamplingConfig(samplingRatio = 10)),
                logs = listOf(LogSamplingConfig(samplingRatio = 20))
            )

            customSampler.setConfig(config)

            assertTrue(customSampler.isSamplingEnabled())
        }

        @Test
        fun `should return true for isSamplingEnabled when config has spans`() {
            val config = SamplingConfig(
                spans = listOf(SpanSamplingConfig(samplingRatio = 10))
            )

            customSampler.setConfig(config)

            assertTrue(customSampler.isSamplingEnabled())
        }

        @Test
        fun `should return true for isSamplingEnabled when config has logs`() {
            val config = SamplingConfig(
                logs = listOf(LogSamplingConfig(samplingRatio = 10))
            )

            customSampler.setConfig(config)

            assertTrue(customSampler.isSamplingEnabled())
        }

        @Test
        fun `should return false for isSamplingEnabled when config is null`() {
            customSampler.setConfig(null)

            assertFalse(customSampler.isSamplingEnabled())
        }

        @Test
        fun `should return false for isSamplingEnabled when config has no spans or logs`() {
            val config = SamplingConfig()

            customSampler.setConfig(config)

            assertFalse(customSampler.isSamplingEnabled())
        }
    }

    @Nested
    @DisplayName("Span Sampling Tests")
    inner class SpanSamplingTests {

        @Test
        fun `should match span when no match criteria is specified`() {
            val config = SamplingConfig(
                spans = listOf(SpanSamplingConfig(samplingRatio = 10))
            )
            customSampler.setConfig(config)

            val span = createMockSpan("test-span", attributes = Attributes.empty())

            every { mockSamplerFunction(10) } returns false

            val result = customSampler.sampleSpan(span)

            assertFalse(result.sample)
            assertEquals(10L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should match span when no config is specified`() {
            val span = createMockSpan("test-span", attributes = Attributes.empty())

            every { mockSamplerFunction(10) } returns false

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertNull(result.attributes)
        }

        @Test
        fun `should match span based on exact name`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        name = MatchConfig.Value("test-span"),
                        samplingRatio = 42
                    )
                )
            )
            customSampler.setConfig(config)

            val span = createMockSpan("test-span")
            every { mockSamplerFunction(42) } returns true

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertEquals(42L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should not match span when name does not match`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        name = MatchConfig.Value("test-span"),
                        samplingRatio = 42
                    )
                )
            )
            customSampler.setConfig(config)

            val span = createMockSpan("other-span")

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertNull(result.attributes)
        }

        @Test
        fun `should match span based on regex name`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        name = MatchConfig.Regex("test-span-\\d+"), // Matches "test-span-" followed by one or more digits.
                        samplingRatio = 42
                    )
                )
            )
            customSampler.setConfig(config)

            val span = createMockSpan("test-span-123")
            every { mockSamplerFunction(42) } returns true

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertEquals(42L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should match span based on string attribute value`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        attributes = listOf(
                            AttributeMatchConfig(
                                key = MatchConfig.Value("http.method"),
                                attribute = MatchConfig.Value("POST")
                            )
                        ),
                        samplingRatio = 75
                    )
                )
            )
            customSampler.setConfig(config)

            val attributes = Attributes.builder()
                .put("http.method", "POST")
                .put("http.url", "https://api.example.com/data")
                .build()
            val span = createMockSpan("http-request", attributes = attributes)
            every { mockSamplerFunction(75) } returns true

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertEquals(75L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should match span based on numeric attribute value`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        attributes = listOf(
                            AttributeMatchConfig(
                                key = MatchConfig.Value("http.status_code"),
                                attribute = MatchConfig.Value(500)
                            )
                        ),
                        samplingRatio = 100
                    )
                )
            )
            customSampler.setConfig(config)

            val attributes = Attributes.builder()
                .put("http.status_code", 500L)
                .put("http.method", "POST")
                .build()
            val span = createMockSpan("http-response", attributes = attributes)
            every { mockSamplerFunction(100) } returns true

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertEquals(100L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should match span based on event name`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        events = listOf(
                            SpanEventMatchConfig(
                                name = MatchConfig.Value("test-event")
                            )
                        ),
                        samplingRatio = 42
                    )
                )
            )
            customSampler.setConfig(config)

            val event = createMockEvent("test-event")
            val span = createMockSpan("test-span", events = listOf(event))
            every { mockSamplerFunction(42) } returns true

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertEquals(42L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should match span based on event attributes`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        events = listOf(
                            SpanEventMatchConfig(
                                attributes = listOf(
                                    AttributeMatchConfig(
                                        key = MatchConfig.Value("error.type"),
                                        attribute = MatchConfig.Value("network")
                                    )
                                )
                            )
                        ),
                        samplingRatio = 85
                    )
                )
            )
            customSampler.setConfig(config)

            val eventAttributes = Attributes.builder()
                .put("error.type", "network")
                .put("error.code", 503L)
                .build()
            val event = createMockEvent("error-event", attributes = eventAttributes)
            val span = createMockSpan("api-request", events = listOf(event))
            every { mockSamplerFunction(85) } returns true

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertEquals(85L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should not match when event attributes do not match`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        events = listOf(
                            SpanEventMatchConfig(
                                attributes = listOf(
                                    AttributeMatchConfig(
                                        key = MatchConfig.Value("error.type"),
                                        attribute = MatchConfig.Value("database")
                                    )
                                )
                            )
                        ),
                        samplingRatio = 85
                    )
                )
            )
            customSampler.setConfig(config)

            val eventAttributes = Attributes.builder()
                .put("error.type", "network")
                .put("error.code", 503L)
                .build()
            val event = createMockEvent("error-event", attributes = eventAttributes)
            val span = createMockSpan("api-request", events = listOf(event))

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertNull(result.attributes)
        }

        @Test
        fun `should handle complex matching with multiple criteria`() {
            val config = SamplingConfig(
                spans = listOf(
                    SpanSamplingConfig(
                        name = MatchConfig.Regex("complex-span-\\d+"), // Matches "complex-span-" followed by one or more digits.
                        attributes = listOf(
                            AttributeMatchConfig(
                                key = MatchConfig.Value("http.method"),
                                attribute = MatchConfig.Value("POST")
                            ),
                            AttributeMatchConfig(
                                key = MatchConfig.Regex("http\\.status.*"), // Matches any string starting with "http.status" followed by zero or more characters.
                                attribute = MatchConfig.Value(500)
                            )
                        ),
                        samplingRatio = 50
                    )
                )
            )
            customSampler.setConfig(config)

            val attributes = Attributes.builder()
                .put("http.method", "POST")
                .put("http.status_code", 500L)
                .put("url", "https://api.example.com/users")
                .put("retry", true)
                .build()
            val span = createMockSpan("complex-span-123", attributes = attributes)
            every { mockSamplerFunction(50) } returns true

            val result = customSampler.sampleSpan(span)

            assertTrue(result.sample)
            assertEquals(50L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }
    }

    @Nested
    @DisplayName("Log Sampling Tests")
    inner class LogSamplingTests {

        @Test
        fun `should match log based on severity`() {
            val config = SamplingConfig(
                logs = listOf(
                    LogSamplingConfig(
                        severityText = MatchConfig.Value("ERROR"),
                        samplingRatio = 42
                    )
                )
            )
            customSampler.setConfig(config)

            val log = createMockLog(severityText = "ERROR")
            every { mockSamplerFunction(42) } returns true

            val result = customSampler.sampleLog(log)

            assertTrue(result.sample)
            assertEquals(42L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should not match log when severity does not match`() {
            val config = SamplingConfig(
                logs = listOf(
                    LogSamplingConfig(
                        severityText = MatchConfig.Value("ERROR"),
                        samplingRatio = 42
                    )
                )
            )
            customSampler.setConfig(config)

            val log = createMockLog(severityText = "INFO")

            val result = customSampler.sampleLog(log)

            assertTrue(result.sample)
            assertNull(result.attributes)
        }

        @Test
        fun `should match log based on message with exact value`() {
            val config = SamplingConfig(
                logs = listOf(
                    LogSamplingConfig(
                        message = MatchConfig.Value("Connection failed"),
                        samplingRatio = 42
                    )
                )
            )
            customSampler.setConfig(config)

            val log = createMockLog(message = "Connection failed")
            every { mockSamplerFunction(42) } returns true

            val result = customSampler.sampleLog(log)

            assertTrue(result.sample)
            assertEquals(42L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should match log based on message with regex`() {
            val config = SamplingConfig(
                logs = listOf(
                    LogSamplingConfig(
                        message = MatchConfig.Regex("Error: .*"), // Matches any string that starts with "Error:" followed by any characters
                        samplingRatio = 42
                    )
                )
            )
            customSampler.setConfig(config)

            val log = createMockLog(message = "Error: Connection timed out")
            every { mockSamplerFunction(42) } returns true

            val result = customSampler.sampleLog(log)

            assertTrue(result.sample)
            assertEquals(42L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should match log based on string attribute value`() {
            val config = SamplingConfig(
                logs = listOf(
                    LogSamplingConfig(
                        attributes = listOf(
                            AttributeMatchConfig(
                                key = MatchConfig.Value("service.name"),
                                attribute = MatchConfig.Value("api-gateway")
                            )
                        ),
                        samplingRatio = 75
                    )
                )
            )
            customSampler.setConfig(config)

            val attributes = Attributes.builder()
                .put("service.name", "api-gateway")
                .put("environment", "production")
                .build()
            val log = createMockLog(attributes = attributes)
            every { mockSamplerFunction(75) } returns true

            val result = customSampler.sampleLog(log)

            assertTrue(result.sample)
            assertEquals(75L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should not match log when attributes do not exist`() {
            val config = SamplingConfig(
                logs = listOf(
                    LogSamplingConfig(
                        attributes = listOf(
                            AttributeMatchConfig(
                                key = MatchConfig.Value("service.name"),
                                attribute = MatchConfig.Value("api-gateway")
                            )
                        ),
                        samplingRatio = 75
                    )
                )
            )
            customSampler.setConfig(config)

            val log = createMockLog(message = "Connection failed")

            val result = customSampler.sampleLog(log)

            assertTrue(result.sample)
            assertNull(result.attributes)
        }

        @Test
        fun `should handle complex log matching with multiple criteria`() {
            val config = SamplingConfig(
                logs = listOf(
                    LogSamplingConfig(
                        message = MatchConfig.Regex("Database connection .*"),
                        severityText = MatchConfig.Value("ERROR"),
                        attributes = listOf(
                            AttributeMatchConfig(
                                key = MatchConfig.Regex("service.*"),
                                attribute = MatchConfig.Regex("db-.*")
                            ),
                            AttributeMatchConfig(
                                key = MatchConfig.Value("retry.enabled"),
                                attribute = MatchConfig.Value(true)
                            )
                        ),
                        samplingRatio = 90
                    )
                )
            )
            customSampler.setConfig(config)

            val attributes = Attributes.builder()
                .put("service.name", "db-connector")
                .put("retry.enabled", true)
                .put("retry.count", 3L)
                .build()

            val log = createMockLog(
                message = "Database connection failed: timeout",
                severityText = "ERROR",
                attributes = attributes
            )
            every { mockSamplerFunction(90) } returns true

            val result = customSampler.sampleLog(log)

            assertTrue(result.sample)
            assertEquals(90L, result.attributes?.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }
    }

    @Nested
    @DisplayName("Default Sampler Function Tests")
    inner class DefaultSamplerTests {

        @Test
        fun `defaultSampler should always return true for ratio 1`() {
            assertTrue(defaultSampler(1))
        }

        @Test
        fun `defaultSampler should always return false for ratio 0`() {
            assertFalse(defaultSampler(0))
        }

        @Test
        fun `defaultSampler returns true when Random is 0 and false otherwise`() {
            mockkObject(Random.Default)
            try {
                every { Random.nextInt(5) } returns 0
                val result1 = defaultSampler(5)
                assertTrue(result1)

                every { Random.nextInt(5) } returns 3
                val result2 = defaultSampler(5)
                assertFalse(result2)
            } finally {
                unmockkObject(Random.Default)
            }
        }
    }

    private fun createMockSpan(
        name: String,
        attributes: Attributes = Attributes.empty(),
        events: List<EventData> = emptyList()
    ): SpanData {
        return mockk<SpanData>().apply {
            every { getName() } returns name
            every { getAttributes() } returns attributes
            every { getEvents() } returns events
        }
    }

    private fun createMockEvent(
        name: String,
        attributes: Attributes = Attributes.empty()
    ): EventData {
        return mockk<EventData>().apply {
            every { getName() } returns name
            every { getAttributes() } returns attributes
        }
    }

    private fun createMockLog(
        message: String? = null,
        severityText: String? = null,
        attributes: Attributes = Attributes.empty()
    ): LogRecordData {
        return mockk<LogRecordData>().apply {
            every { getBodyValue()?.asString() } returns message

            if (severityText != null) {
                val severity = mockk<Severity>()
                every { severity.name } returns severityText
                every { getSeverity() } returns severity
            } else {
                every { severity } returns null
            }

            every { getAttributes() } returns attributes
        }
    }
}
