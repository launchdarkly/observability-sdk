package com.launchdarkly.observability.client

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLifecycleTrackerTest {
    /** A test [LifecycleOwner] whose state can be driven directly, off the main thread. */
    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `suppresses catch-up foreground and emits on real transitions`() {
        val owner = TestLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val signals = mutableListOf<AppLifecycleSignal>()
        val tracker = AppLifecycleTracker(onSignal = { signals.add(it) }, lifecycleOwnerProvider = { owner })

        tracker.start()
        // The synchronous catch-up onStart (app already foregrounded) must not emit a foreground.
        assertTrue(signals.isEmpty())

        owner.registry.currentState = Lifecycle.State.CREATED // -> onStop (background)
        owner.registry.currentState = Lifecycle.State.RESUMED // -> onStart (foreground)

        assertEquals(2, signals.size)
        assertEquals(AppLifecycleSignal.Kind.BACKGROUND, signals[0].kind)
        assertEquals("background", signals[0].lifecycleState)
        assertEquals(AppLifecycleSignal.Kind.FOREGROUND, signals[1].kind)
        assertEquals("foreground", signals[1].lifecycleState)
    }

    @Test
    fun `stop halts emission`() {
        val owner = TestLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val signals = mutableListOf<AppLifecycleSignal>()
        val tracker = AppLifecycleTracker(onSignal = { signals.add(it) }, lifecycleOwnerProvider = { owner })

        tracker.start()
        tracker.stop()
        owner.registry.currentState = Lifecycle.State.CREATED // would be background, but stopped

        assertTrue(signals.isEmpty())
    }
}
