package com.launchdarkly.observability.client

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import com.launchdarkly.observability.R
import com.launchdarkly.observability.testing.ObservabilityMainThreadTestHooks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [UserInteractionManager]'s native `ldId(...)` tag resolution and for what it reports
 * about an activity that resumed before an instrumentation installed.
 */
class UserInteractionManagerTest {
    private val manager = UserInteractionManager()

    // Touch capture confines its window mutation to the main thread, which this replaces with a
    // synchronous executor so the plain JVM test doesn't need a Looper.
    @BeforeEach
    fun setUp() = ObservabilityMainThreadTestHooks.overrideWithSynchronous()

    @AfterEach
    fun tearDown() = ObservabilityMainThreadTestHooks.reset()

    private fun mockView(ldId: String? = null, parent: ViewGroup? = null): View =
        mockk<View>(relaxed = true).also {
            every { it.getTag(R.id.ld_id_tag) } returns ldId
            every { it.parent } returns parent
        }

    private fun mockGroup(ldId: String? = null, parent: ViewGroup? = null): ViewGroup =
        mockk<ViewGroup>(relaxed = true).also {
            every { it.getTag(R.id.ld_id_tag) } returns ldId
            every { it.parent } returns parent
        }

    @Test
    fun `resolveLdId returns the id set directly on the view`() {
        val view = mockView(ldId = "checkout.pay_button")
        assertEquals("checkout.pay_button", manager.resolveLdId(view))
    }

    @Test
    fun `resolveLdId walks up to the nearest ancestor carrying an id`() {
        val grandparent = mockGroup(ldId = "card.root")
        val parent = mockGroup(ldId = null, parent = grandparent)
        val child = mockView(ldId = null, parent = parent)
        assertEquals("card.root", manager.resolveLdId(child))
    }

    @Test
    fun `resolveLdId prefers the closest ancestor`() {
        val grandparent = mockGroup(ldId = "card.root")
        val parent = mockGroup(ldId = "card.cta", parent = grandparent)
        val child = mockView(ldId = null, parent = parent)
        assertEquals("card.cta", manager.resolveLdId(child))
    }

    @Test
    fun `resolveLdId returns null when no view in the chain has an id`() {
        val parent = mockGroup(ldId = null)
        val child = mockView(ldId = null, parent = parent)
        assertNull(manager.resolveLdId(child))
    }

    @Test
    fun `resolveLdId ignores empty ids`() {
        val view = mockView(ldId = "")
        assertNull(manager.resolveLdId(view))
    }

    private fun mockActivity(): Pair<Activity, Window> {
        val window = mockk<Window>(relaxed = true)
        val activity = mockk<Activity>(relaxed = true)
        every { activity.window } returns window
        return activity to window
    }

    @Test
    fun `the window of an activity that resumed before capture was enabled is still wrapped`() {
        val (activity, window) = mockActivity()

        // The order a late install produces: the activity resumes while only lifecycle tracking is
        // attached, and capture is enabled afterwards - with no further callback to rely on.
        manager.onActivityResumed(activity)
        manager.enableTouchCapture()

        verify { window.callback = any() }
    }

    @Test
    fun `the resumed activity is reported so a late instrumentation can register it`() {
        val (activity, _) = mockActivity()

        manager.onActivityResumed(activity)

        assertSame(activity, manager.currentActivity)
    }

    @Test
    fun `registering an activity that resumed unseen wraps its window and reports it`() {
        val (activity, window) = mockActivity()

        // The recovery ObservabilityService performs when it installs after an activity resumed:
        // no lifecycle callback ever arrives for that activity, so this is all the manager gets.
        manager.enableTouchCapture()
        manager.registerActivity(activity)

        verify { window.callback = any() }
        assertSame(activity, manager.currentActivity)
    }

    @Test
    fun `no activity is reported once the resumed one pauses`() {
        val (activity, _) = mockActivity()
        manager.onActivityResumed(activity)

        manager.onActivityPaused(activity)

        assertNull(manager.currentActivity)
    }
}
