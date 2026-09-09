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

    @Test
    fun `should delegate identify to underlying Observe implementation`() {
        ldObserve.identify(mapOf("org" to "org-key"), "org:org-key", mapOf("plan" to "pro"))

        verify(exactly = 1) {
            mockObserve.identify(mapOf("org" to "org-key"), "org:org-key", mapOf("plan" to "pro"))
        }
    }

    @Test
    fun `should identify a key as a single-kind user context`() {
        ldObserve.identify("user-key")

        verify(exactly = 1) { mockObserve.identify(mapOf("user" to "user-key"), "user-key", null) }
    }
}
