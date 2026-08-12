package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.sampling.utils.FakeExportSampler
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.Value
import io.opentelemetry.sdk.logs.data.LogRecordData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class SampleLogsTest {

    @Nested
    @DisplayName("Sampling Disabled Tests")
    inner class SamplingDisabledTests {

        @Test
        fun `should return all logs when sampling is disabled`() {
            val sampler = FakeExportSampler(isSamplingEnabled = { false })

            val logs = listOf(
                createMockLog("log1"),
                createMockLog("log2"),
                createMockLog("log3")
            )

            val result = sampleLogs(logs, sampler)

            assertEquals(logs, result)
            assertEquals(3, result.size)
        }
    }

    @Nested
    @DisplayName("Sampling Enabled Tests")
    inner class SamplingEnabledTests {

        @Test
        fun `should handle empty input list`() {
            val sampler = FakeExportSampler(isSamplingEnabled = { true })

            val result = sampleLogs(emptyList(), sampler)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list when no logs are sampled`() {
            val sampler = FakeExportSampler(
                isSamplingEnabled = { true },
                sampleLog = { SamplingResult(sample = false) }
            )

            val logs = listOf(
                createMockLog("log1"),
                createMockLog("log2"),
                createMockLog("log3")
            )

            val result = sampleLogs(logs, sampler)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return all logs when all are sampled without additional attributes`() {
            val sampler = FakeExportSampler(
                isSamplingEnabled = { true },
                sampleLog = { SamplingResult(sample = true, attributes = null) }
            )

            val logs = listOf(
                createMockLog("log1"),
                createMockLog("log2"),
                createMockLog("log3")
            )

            val result = sampleLogs(logs, sampler)

            assertEquals(logs, result)
            assertEquals(3, result.size)
        }

        @Test
        fun `should return subset when some logs are sampled`() {
            val sampler = FakeExportSampler(
                isSamplingEnabled = { true },
                sampleLog = {
                    if (it.bodyValue?.value == "log2") {
                        SamplingResult(sample = false)
                    } else {
                        SamplingResult(sample = true)
                    }
                }
            )

            val log1 = createMockLog("log1")
            val log2 = createMockLog("log2")
            val log3 = createMockLog("log3")
            val logs = listOf(log1, log2, log3)

            val result = sampleLogs(logs, sampler)

            assertEquals(2, result.size)
            assertEquals(log1, result[0])
            assertEquals(log3, result[1])
        }

        @Test
        fun `should add sampling attributes to logs when provided and preserve the original ones`() {
            val originalAttributes = Attributes.builder()
                .put("service.name", "api-service")
                .put("environment", "production")
                .build()

            val originalLog = createMockLog("test-log", attributes = originalAttributes)
            val logs = listOf(originalLog)

            val samplingAttributes = Attributes.builder()
                .put("launchdarkly.sampling.ratio", 42L)
                .build()

            val sampler = FakeExportSampler(
                isSamplingEnabled = { true },
                sampleLog = {
                    if (it.bodyValue?.value == "test-log") {
                        SamplingResult(sample = true, attributes = samplingAttributes)
                    } else {
                        SamplingResult(sample = false)
                    }
                }
            )

            val result = sampleLogs(logs, sampler)

            assertEquals(1, result.size)
            assertNotSame(originalLog, result[0]) // Should be a new instance with merged attributes

            // Verify the result has both original and merged attributes
            val resultAttributes = result[0].attributes
            assertEquals("api-service", resultAttributes.get(AttributeKey.stringKey("service.name")))
            assertEquals("production", resultAttributes.get(AttributeKey.stringKey("environment")))
            assertEquals(42L, resultAttributes.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }

        @Test
        fun `should handle mixed sampling results with and without attributes`() {
            val log1 = createMockLog("log1")
            val log2 = createMockLog("log2")
            val log3 = createMockLog("log3")
            val log4 = createMockLog("log4")
            val logs = listOf(log1, log2, log3, log4)

            val samplingAttributes = Attributes.builder()
                .put("launchdarkly.sampling.ratio", 50L)
                .build()

            val sampler = FakeExportSampler(
                isSamplingEnabled = { true },
                sampleLog = {
                    when (it.bodyValue?.value) {
                        "log1" -> SamplingResult(sample = true, attributes = null)
                        "log3" -> SamplingResult(sample = true, attributes = samplingAttributes)
                        else -> SamplingResult(sample = false)
                    }
                }
            )

            val result = sampleLogs(logs, sampler)

            assertEquals(2, result.size)

            // Log1 should be unchanged (no attributes added)
            assertEquals(log1, result[0])

            // Log3 should be modified (attributes added)
            assertNotSame(log3, result[1])
            assertEquals(50L, result[1].attributes.get(AttributeKey.longKey("launchdarkly.sampling.ratio")))
        }
    }

    private fun createMockLog(
        name: String,
        attributes: Attributes = Attributes.empty()
    ): LogRecordData {
        return mockk<LogRecordData>().apply {
            every { bodyValue } returns Value.of(name)
            every { eventName } returns name
            every { getAttributes() } returns attributes
        }
    }
}
