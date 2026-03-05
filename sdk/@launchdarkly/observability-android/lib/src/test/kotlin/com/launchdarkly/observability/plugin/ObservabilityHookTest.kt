package com.launchdarkly.observability.plugin

import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ObservabilityHookTest {

    private lateinit var mockExporter: ObservabilityHookExporter
    private lateinit var mockContext: LDContext
    private lateinit var hook: ObservabilityHook

    @BeforeEach
    fun setup() {
        mockExporter = mockk(relaxed = true)
        mockContext = mockk {
            every { fullyQualifiedKey } returns "test-user-123"
            every { isMultiple } returns false
            every { kind } returns ContextKind.DEFAULT
            every { key } returns "test-user-123"
        }
        hook = ObservabilityHook(mockExporter)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("Before Identify Tests")
    inner class BeforeIdentifyTests {

        @Test
        fun `should return seriesData unchanged`() {
            val seriesContext = IdentifySeriesContext(mockContext, 5)
            val seriesData = mapOf<String, Any>("test" to "empty_data")

            val result = hook.beforeIdentify(seriesContext, seriesData)

            assertEquals(seriesData, result)
        }
    }

    @Nested
    @DisplayName("After Identify Tests")
    inner class AfterIdentifyTests {

        @Test
        fun `should call exporter afterIdentify with correct context keys on completion`() {
            val seriesContext = IdentifySeriesContext(mockContext, 5)
            val seriesData = mapOf<String, Any>("test" to "empty_data")
            val identifyResult = IdentifySeriesResult(IdentifySeriesResult.IdentifySeriesStatus.COMPLETED)

            hook.afterIdentify(seriesContext, seriesData, identifyResult)

            verify(exactly = 1) {
                mockExporter.afterIdentify(
                    contextKeys = mapOf(ContextKind.DEFAULT.toString() to "test-user-123"),
                    canonicalKey = "test-user-123",
                    completed = true
                )
            }
        }

        @Test
        fun `should pass completed false for non-completed result`() {
            val seriesContext = IdentifySeriesContext(mockContext, 5)
            val seriesData = mapOf<String, Any>("test" to "empty_data")
            val identifyResult = IdentifySeriesResult(IdentifySeriesResult.IdentifySeriesStatus.ERROR)

            hook.afterIdentify(seriesContext, seriesData, identifyResult)

            verify(exactly = 1) {
                mockExporter.afterIdentify(
                    contextKeys = any(),
                    canonicalKey = "test-user-123",
                    completed = false
                )
            }
        }

        @Test
        fun `should extract multi-kind context keys`() {
            val userContext = mockk<LDContext> {
                every { kind } returns ContextKind.DEFAULT
                every { key } returns "user-key"
            }
            val orgContext = mockk<LDContext> {
                every { kind } returns ContextKind.of("org")
                every { key } returns "org-key"
            }
            val multiContext = mockk<LDContext> {
                every { fullyQualifiedKey } returns "multi-key"
                every { isMultiple } returns true
                every { individualContextCount } returns 2
                every { getIndividualContext(0) } returns userContext
                every { getIndividualContext(1) } returns orgContext
            }

            val seriesContext = IdentifySeriesContext(multiContext, 5)
            val seriesData = mapOf<String, Any>("test" to "empty_data")
            val identifyResult = IdentifySeriesResult(IdentifySeriesResult.IdentifySeriesStatus.COMPLETED)

            val hookForMulti = ObservabilityHook(mockExporter)
            hookForMulti.afterIdentify(seriesContext, seriesData, identifyResult)

            verify(exactly = 1) {
                mockExporter.afterIdentify(
                    contextKeys = match { keys ->
                        keys[ContextKind.DEFAULT.toString()] == "user-key" &&
                        keys["org"] == "org-key" &&
                        keys.size == 2
                    },
                    canonicalKey = "multi-key",
                    completed = true
                )
            }
        }
    }

    @Nested
    @DisplayName("Before Evaluation Tests")
    inner class BeforeEvaluationTests {

        @Test
        fun `should call exporter beforeEvaluation and store evalId in seriesData`() {
            val seriesContext = com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext(
                "evaluation",
                "test-flag",
                mockContext,
                com.launchdarkly.sdk.LDValue.ofNull()
            )
            val seriesData = mapOf<String, Any>("existing" to "data")

            val result = hook.beforeEvaluation(seriesContext, seriesData)

            assertTrue(result.containsKey(ObservabilityHookExporter.DATA_KEY_EVAL_ID))
            assertEquals("data", result["existing"])
            verify(exactly = 1) {
                mockExporter.beforeEvaluation(
                    evaluationId = any(),
                    flagKey = "test-flag",
                    contextKey = "test-user-123"
                )
            }
        }
    }
}
