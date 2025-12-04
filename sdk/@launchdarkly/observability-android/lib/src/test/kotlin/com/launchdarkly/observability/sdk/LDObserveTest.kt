package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.interfaces.Observe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LDObserveTest {

    private lateinit var ldObserve: LDObserve
    private lateinit var mockObserve: Observe

    @BeforeEach
    fun setup() {
        mockObserve = mockk {
            every { flush() } returns true
        }
        ldObserve = LDObserve(mockObserve)
    }

    @Test
    fun `should delegate flush to underlying Observe implementation and propagate result`() {
        val result = ldObserve.flush()

        assertTrue(result)
        verify(exactly = 1) { mockObserve.flush() }
    }
}
