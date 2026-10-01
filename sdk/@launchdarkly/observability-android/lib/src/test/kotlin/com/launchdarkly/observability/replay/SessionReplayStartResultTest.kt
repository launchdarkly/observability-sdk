package com.launchdarkly.observability.replay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionReplayStartResultTest {

    @Test
    fun `isRunning is true only for the outcomes that leave replay recording`() {
        // Bridges surface this to callers who cannot see the individual cases, and it has to agree
        // with the Swift SDK's `SessionReplayStartResult.isRunning`.
        val recording = SessionReplayStartResult.entries.filter { it.isRunning }

        assertEquals(
            listOf(SessionReplayStartResult.STARTED, SessionReplayStartResult.ALREADY_STARTED),
            recording,
        )
    }
}
