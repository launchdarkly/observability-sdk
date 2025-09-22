package com.launchdarkly.observability.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObservabilityClientTest {

    private lateinit var mockInstrumentationManager: InstrumentationManager
    private lateinit var observabilityClient: ObservabilityClient

    @BeforeEach
    fun setup() {
        mockInstrumentationManager = mockk {
            every { flush() } returns true
        }

        observabilityClient = ObservabilityClient(mockInstrumentationManager)
    }

    @Test
    fun `should delegate flush to underlying InstrumentationManager and propagate result`() {
        val result = observabilityClient.flush()

        assertTrue(result)
        verify(exactly = 1) { mockInstrumentationManager.flush() }
    }
}
