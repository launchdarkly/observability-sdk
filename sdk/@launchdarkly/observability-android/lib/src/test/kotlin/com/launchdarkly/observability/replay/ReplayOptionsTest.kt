package com.launchdarkly.observability.replay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReplayOptionsTest {

    @Test
    fun `frameRate defaults to one frame per second`() {
        assertEquals(1.0, ReplayOptions().frameRate)
    }

    @Test
    fun `frameRate can be configured`() {
        assertEquals(2.0, ReplayOptions(frameRate = 2.0).frameRate)
    }
}
