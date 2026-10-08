package com.launchdarkly.observability.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClickEventTest {
    @Test
    fun `manual and embedder clicks stamp the span at the gesture timestamp`() {
        val click = ClickEvent(timestamp = 1_700_000_000_123L)

        val (startMs, endMs) = click.spanWindow()

        assertEquals(1_700_000_000_123L, startMs)
        assertEquals(1_700_000_000_123L, endMs)
    }

    @Test
    fun `automatic taps keep the ACTION_DOWN to ACTION_UP window`() {
        val upTime = 1_700_000_000_480L
        val click = ClickEvent(timestamp = upTime)

        val (startMs, endMs) = click.spanWindow(
            spanStartTimeMs = 1_700_000_000_400L,
            spanEndTimeMs = upTime,
        )

        assertEquals(1_700_000_000_400L, startMs)
        assertEquals(upTime, endMs)
    }
}
