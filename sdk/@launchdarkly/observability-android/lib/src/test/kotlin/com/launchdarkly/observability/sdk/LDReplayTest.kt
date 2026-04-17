package com.launchdarkly.observability.sdk

import android.app.Activity
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LDReplayTest {

    private class TestControl : SessionReplayServicing {
        var startCalls = 0
        var stopCalls = 0
        var flushCalls = 0
        var registerActivityCalls = 0

        override fun start() {
            startCalls++
        }

        override fun stop() {
            stopCalls++
        }

        override fun flush() {
            flushCalls++
        }

        override fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {}

        override fun registerActivity(activity: Activity) {
            registerActivityCalls++
        }
    }

    @Test
    fun `start delegates to replay control`() {
        val control = TestControl()
        LDReplay.init(control)

        LDReplay.start()

        assertEquals(1, control.startCalls)
    }

    @Test
    fun `stop delegates to replay control`() {
        val control = TestControl()
        LDReplay.init(control)

        LDReplay.stop()

        assertEquals(1, control.stopCalls)
    }

    @Test
    fun `flush delegates to replay control`() {
        val control = TestControl()
        LDReplay.init(control)

        LDReplay.flush()

        assertEquals(1, control.flushCalls)
    }

    @Test
    fun `registerActivity delegates to replay control`() {
        val control = TestControl()
        LDReplay.init(control)

        LDReplay.registerActivity(mockk<Activity>())

        assertEquals(1, control.registerActivityCalls)
    }
}
