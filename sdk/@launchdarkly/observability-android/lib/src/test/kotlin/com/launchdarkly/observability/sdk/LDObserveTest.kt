package com.launchdarkly.observability.sdk

import android.view.ViewConfiguration
import com.launchdarkly.observability.client.ObservabilityService
import com.launchdarkly.observability.client.UserInteractionManager
import com.launchdarkly.observability.interfaces.Observe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `should preserve positional trackClick arguments when delegating`() {
        ldObserve.trackClick("element-id", "Button", "Visible label")

        verify(exactly = 1) {
            mockObserve.trackClick(
                "element-id",
                "Button",
                "Visible label",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            )
        }
    }

    /**
     * The embedder's plugin installs its click detection independently of observability init, so the
     * handshake regularly arrives first. If it were dropped, native detection would keep describing
     * the embedder surface and every tap would be reported twice.
     */
    @Test
    fun `should apply embedder click handling requested before initialization`() {
        // ObservabilityService reads its tap thresholds from ViewConfiguration when the class loads,
        // which the JVM's stubbed android.jar refuses to answer.
        mockkStatic(ViewConfiguration::class)
        every { ViewConfiguration.getLongPressTimeout() } returns 500

        val userInteractionManager = UserInteractionManager()
        val service: ObservabilityService = mockk(relaxed = true)
        every { service.userInteractionManager } returns userInteractionManager

        LDObserve.setEmbedderClickHandling(true)
        LDObserve.init(service)

        assertTrue(userInteractionManager.embedderHandlesClicks)
    }

    @AfterEach
    fun resetEmbedderClickHandling() {
        // Companion state is process-wide; leave it off so later tests see the default.
        LDObserve.setEmbedderClickHandling(false)
        unmockkStatic(ViewConfiguration::class)
    }
}
