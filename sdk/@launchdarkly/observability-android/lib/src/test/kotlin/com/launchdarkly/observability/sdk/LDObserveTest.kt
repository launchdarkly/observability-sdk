package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.interfaces.Observe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LDObserveTest {

    private lateinit var ldObserve: LDObserve
    private lateinit var mockObserve: Observe

    @BeforeEach
    fun setup() {
        mockObserve = mockk(relaxed = true)
        ldObserve = LDObserve(mockObserve)
    }

    @Test
    fun `should delegate flush to underlying Observe implementation`() {
        ldObserve.flush()

        verify(exactly = 1) { mockObserve.flush() }
    }
}
