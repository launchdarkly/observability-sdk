package com.launchdarkly.observability.sdk

import android.app.Activity
import com.launchdarkly.observability.testing.ObservabilityMainThreadTestHooks
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LDReplayTest {

    private class TestReplayService(initialIsEnabled: Boolean = false) : SessionReplayServicing {
        override var isEnabled: Boolean = initialIsEnabled
        var flushCalls = 0
        val registeredActivities = mutableListOf<Activity>()
        val identifyCalls = mutableListOf<IdentifyCall>()

        val registerActivityCalls: Int get() = registeredActivities.size
        val afterIdentifyCalls: Int get() = identifyCalls.size

        override fun flush() {
            flushCalls++
        }

        override fun afterIdentify(
            contextKeys: Map<String, String>,
            canonicalKey: String,
            completed: Boolean
        ) {
            identifyCalls += IdentifyCall(contextKeys, canonicalKey, completed)
        }

        override fun registerActivity(activity: Activity) {
            registeredActivities += activity
        }

        data class IdentifyCall(
            val contextKeys: Map<String, String>,
            val canonicalKey: String,
            val completed: Boolean,
        )
    }

    @BeforeEach
    fun setUp() {
        // LDReplay is a global singleton; reset every piece of state between tests.
        LDReplay.resetForTest()
        // Post-init writes through PreInitReplayBuffer dispatch via runOnMainThread, which would
        // hit Android's main Looper. Swap to a synchronous executor for the duration of the test.
        ObservabilityMainThreadTestHooks.overrideWithSynchronous()
    }

    @AfterEach
    fun tearDown() {
        LDReplay.resetForTest()
        ObservabilityMainThreadTestHooks.reset()
    }

    @Test
    fun `setting isEnabled forwards to active replay service`() {
        val replayService = TestReplayService()
        LDReplay.init(replayService)

        LDReplay.isEnabled = true

        assertTrue(replayService.isEnabled)
    }

    @Test
    fun `start sets isEnabled to true on active replay service`() {
        val replayService = TestReplayService()
        LDReplay.init(replayService)

        LDReplay.start()

        assertTrue(replayService.isEnabled)
    }

    @Test
    fun `stop sets isEnabled to false on active replay service`() {
        val replayService = TestReplayService(initialIsEnabled = true)
        LDReplay.init(replayService)

        LDReplay.stop()

        assertFalse(replayService.isEnabled)
    }

    @Test
    fun `isEnabled set before init is forwarded to replay service during init`() {
        // Simulate a caller flipping the switch before SDK initialization wires up a replay service.
        LDReplay.isEnabled = true

        val replayService = TestReplayService(initialIsEnabled = false)
        LDReplay.init(replayService)

        assertTrue(replayService.isEnabled)
        // After init, reads should reflect the live replay service's state.
        assertTrue(LDReplay.isEnabled)
    }

    @Test
    fun `isEnabled never set before init preserves replay service options-based default`() {
        // No pre-init write: init must not clobber the replay service's `options.enabled`-derived state.
        val replayService = TestReplayService(initialIsEnabled = true)
        LDReplay.init(replayService)

        assertTrue(replayService.isEnabled)
    }

    @Test
    fun `isEnabled getter falls back to buffered value before init`() {
        LDReplay.isEnabled = true

        // Without a wired replay service, the getter should reflect what the user just set.
        assertTrue(LDReplay.isEnabled)
    }

    @Test
    fun `isEnabled getter defaults to false when no replay service and no pre-init value`() {
        assertFalse(LDReplay.isEnabled)
    }

    @Test
    fun `flush delegates to replay service`() {
        val replayService = TestReplayService()
        LDReplay.init(replayService)

        LDReplay.flush()

        assertEquals(1, replayService.flushCalls)
    }

    @Test
    fun `flush is a no-op before init`() {
        // Should not throw or otherwise misbehave.
        LDReplay.flush()
    }

    @Test
    fun `registerActivity delegates to replay service`() {
        val replayService = TestReplayService()
        LDReplay.init(replayService)

        LDReplay.registerActivity(mockk<Activity>())

        assertEquals(1, replayService.registerActivityCalls)
    }

    @Test
    fun `registerActivity before init is replayed to replay service during init`() {
        val activity = mockk<Activity>()
        LDReplay.registerActivity(activity)

        val replayService = TestReplayService()
        LDReplay.init(replayService)

        assertEquals(1, replayService.registerActivityCalls)
        assertEquals(activity, replayService.registeredActivities.single())
    }

    @Test
    fun `multiple registerActivity calls before init are all replayed in order`() {
        val activityA = mockk<Activity>()
        val activityB = mockk<Activity>()
        LDReplay.registerActivity(activityA)
        LDReplay.registerActivity(activityB)

        val replayService = TestReplayService()
        LDReplay.init(replayService)

        assertEquals(listOf(activityA, activityB), replayService.registeredActivities)
    }

    @Test
    fun `afterIdentify before init is replayed to replay service during init`() {
        LDReplay.afterIdentify(mapOf("user" to "abc"), "user:abc", true)

        val replayService = TestReplayService()
        LDReplay.init(replayService)

        assertEquals(1, replayService.afterIdentifyCalls)
        val replayed = replayService.identifyCalls.single()
        assertEquals(mapOf("user" to "abc"), replayed.contextKeys)
        assertEquals("user:abc", replayed.canonicalKey)
        assertTrue(replayed.completed)
    }

    @Test
    fun `latest afterIdentify before init wins over older ones`() {
        LDReplay.afterIdentify(mapOf("user" to "old"), "user:old", true)
        LDReplay.afterIdentify(mapOf("user" to "new"), "user:new", true)

        val replayService = TestReplayService()
        LDReplay.init(replayService)

        // Older identifies are stale; only the most recent one is replayed.
        assertEquals(1, replayService.afterIdentifyCalls)
        assertEquals("user:new", replayService.identifyCalls.single().canonicalKey)
    }

    @Test
    fun `afterIdentify after init forwards directly without buffering`() {
        val replayService = TestReplayService()
        LDReplay.init(replayService)

        LDReplay.afterIdentify(mapOf("user" to "x"), "user:x", true)

        assertEquals(1, replayService.afterIdentifyCalls)
    }

    @Test
    fun `resetForTest clears buffered registerActivity and afterIdentify`() {
        LDReplay.registerActivity(mockk<Activity>())
        LDReplay.afterIdentify(mapOf("user" to "x"), "user:x", true)

        LDReplay.resetForTest()

        val replayService = TestReplayService()
        LDReplay.init(replayService)

        // Nothing should have been replayed; reset wiped the buffers.
        assertEquals(0, replayService.registerActivityCalls)
        assertEquals(0, replayService.afterIdentifyCalls)
    }
}
