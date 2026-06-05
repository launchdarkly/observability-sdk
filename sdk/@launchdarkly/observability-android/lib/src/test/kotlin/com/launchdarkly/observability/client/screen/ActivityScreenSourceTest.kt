package com.launchdarkly.observability.client.screen

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActivityScreenSourceTest {
    /** An [Activity] mock that reports a stable screen name via [LDScreenNameProvider]. */
    private fun providerActivity(name: String): Activity =
        mockk<Activity>(relaxed = true, moreInterfaces = arrayOf(LDScreenNameProvider::class)).also {
            every { (it as LDScreenNameProvider).ldScreenName } returns name
            every { (it as LDScreenNameProvider).ldScreenCategory } returns null
        }

    @Test
    fun `resume captures the screen`() {
        val captured = mutableListOf<ScreenView>()
        val source = ActivityScreenSource { captured.add(it) }

        source.onActivityResumed(providerActivity("Home"))

        assertEquals(listOf("Home"), captured.map { it.name })
    }

    @Test
    fun `captureCurrent re-emits the resumed activity for session re-seeding`() {
        val captured = mutableListOf<ScreenView>()
        val source = ActivityScreenSource { captured.add(it) }

        source.onActivityResumed(providerActivity("Home"))
        captured.clear()

        // Session reset re-seed: re-emit the still-visible screen even though no resume fired.
        source.captureCurrent()

        assertEquals(listOf("Home"), captured.map { it.name })
    }

    @Test
    fun `captureCurrent is a no-op when nothing is resumed`() {
        val captured = mutableListOf<ScreenView>()
        val source = ActivityScreenSource { captured.add(it) }

        source.captureCurrent()

        assertEquals(emptyList<String>(), captured.map { it.name })
    }

    @Test
    fun `captureCurrent is a no-op after the current activity is paused`() {
        val captured = mutableListOf<ScreenView>()
        val source = ActivityScreenSource { captured.add(it) }
        val activity = providerActivity("Home")

        source.onActivityResumed(activity)
        source.onActivityPaused(activity)
        captured.clear()

        source.captureCurrent()

        assertEquals(emptyList<String>(), captured.map { it.name })
    }
}
