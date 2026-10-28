package com.launchdarkly.observability.api

import io.mockk.mockk
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.trace.SpanProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservabilityOptionsProcessorsTest {

    @Test
    fun `custom processors default to empty`() {
        val options = ObservabilityOptions()

        assertTrue(options.spanProcessors.isEmpty())
        assertTrue(options.logProcessors.isEmpty())
    }

    @Test
    fun `builder sets custom processors in order`() {
        val spanA = mockk<SpanProcessor>()
        val spanB = mockk<SpanProcessor>()
        val log = mockk<LogRecordProcessor>()

        val options = ObservabilityOptions.builder()
            .spanProcessors(listOf(spanA, spanB))
            .logProcessors(listOf(log))
            .build()

        assertEquals(listOf(spanA, spanB), options.spanProcessors)
        assertEquals(listOf(log), options.logProcessors)
    }
}
