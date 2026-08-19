package com.launchdarkly.observability.replay.masking

import android.widget.EdgeEffect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [StretchOverscroll]. Exercises [StretchOverscroll.displacementOf] rather than the
 * view-facing entry point, because the latter gates on `Build.VERSION.SDK_INT`, which unit tests
 * report as 0.
 */
class StretchOverscrollTest {
    /**
     * Stand-in for a scroll container. Only the [EdgeEffect]-typed fields and their names matter to
     * the lookup, so this mirrors how `RecyclerView` and Compose's wrapper name theirs rather than
     * subclassing a real container.
     */
    @Suppress("unused")
    private class FakeScroller(
        @JvmField val topEffect: EdgeEffect? = null,
        @JvmField val bottomEffect: EdgeEffect? = null,
        @JvmField val leftEffect: EdgeEffect? = null,
        @JvmField val unnamedEffect: EdgeEffect? = null,
    )

    private class NotAScroller(@JvmField val label: String = "")

    private fun pulledBy(distance: Float) = mockk<EdgeEffect>(relaxed = true).also {
        every { it.distance } returns distance
    }

    @Test
    fun `an idle container is not displaced`() {
        val scroller = FakeScroller(topEffect = pulledBy(0f), bottomEffect = pulledBy(0f))

        assertNull(StretchOverscroll.displacementOf(scroller, width = 1000, height = 2000))
    }

    @Test
    fun `a container without edge effects is not displaced`() {
        assertNull(StretchOverscroll.displacementOf(NotAScroller(), width = 1000, height = 2000))
    }

    @Test
    fun `a vertical pull displaces content vertically only`() {
        val scroller = FakeScroller(topEffect = pulledBy(0.5f), leftEffect = pulledBy(0f))

        val displacement = StretchOverscroll.displacementOf(scroller, width = 1000, height = 2000)

        assertNotNull(displacement)
        assertEquals(0f, displacement!!.dx)
        assertTrue(displacement.dy > 0f)
    }

    @Test
    fun `a horizontal pull displaces content horizontally only`() {
        val scroller = FakeScroller(leftEffect = pulledBy(0.5f), topEffect = pulledBy(0f))

        val displacement = StretchOverscroll.displacementOf(scroller, width = 1000, height = 2000)

        assertNotNull(displacement)
        assertTrue(displacement!!.dx > 0f)
        assertEquals(0f, displacement.dy)
    }

    @Test
    fun `an effect whose name names no edge displaces both axes`() {
        val scroller = FakeScroller(unnamedEffect = pulledBy(0.5f))

        val displacement = StretchOverscroll.displacementOf(scroller, width = 1000, height = 2000)

        assertNotNull(displacement)
        assertTrue(displacement!!.dx > 0f, "an unfamiliar container should over-mask, not under-mask")
        assertTrue(displacement.dy > 0f)
    }

    @Test
    fun `a full pull displaces content by the framework's maximum plus slack`() {
        val scroller = FakeScroller(bottomEffect = pulledBy(1f))

        val displacement = StretchOverscroll.displacementOf(scroller, width = 1000, height = 2000)

        // EdgeEffect caps the stretch at 0.032 of the container, and 25% slack is added on top.
        assertEquals(80f, displacement!!.dy, 0.5f)
    }

    @Test
    fun `a gentle pull follows the framework's curve rather than scaling linearly`() {
        val scroller = FakeScroller(bottomEffect = pulledBy(0.1f))

        val displacement = StretchOverscroll.displacementOf(scroller, width = 1000, height = 2000)

        // A tenth of a pull already spends about two thirds of the effect; scaling the 0.032 cap
        // linearly would have covered only ~8px of the ~26px the framework actually displaces.
        assertEquals(26.5f, displacement!!.dy, 1f)
    }

    @Test
    fun `the largest pull on an axis wins`() {
        val scroller = FakeScroller(topEffect = pulledBy(0.1f), bottomEffect = pulledBy(1f))

        val displacement = StretchOverscroll.displacementOf(scroller, width = 1000, height = 2000)

        assertEquals(80f, displacement!!.dy, 0.5f)
    }

    @Test
    fun `a sub-pixel stretch is not worth growing a mask for`() {
        val scroller = FakeScroller(topEffect = pulledBy(0.001f))

        assertNull(StretchOverscroll.displacementOf(scroller, width = 10, height = 10))
    }

    @Test
    fun `edge effect holders are recognized by their fields`() {
        assertTrue(StretchOverscroll.holdsEdgeEffects(FakeScroller::class.java))
        assertFalse(StretchOverscroll.holdsEdgeEffects(NotAScroller::class.java))
    }
}
