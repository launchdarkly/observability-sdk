package com.launchdarkly.observability.replay.masking

import android.view.View
import android.view.ViewGroup
import com.launchdarkly.observability.context.ObserveLogger
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests how [MaskCollector] applies stretch overscroll to the masks a container emits. The stretch
 * lookup is substituted, since the real one gates on `Build.VERSION.SDK_INT`, which unit tests
 * report as 0.
 *
 * Mask rects come out all-zero here, because `RectF`'s constructor is a stub in unit tests, so an
 * inflated mask is one whose edges have moved off zero.
 */
class MaskCollectorStretchTest {
    private val logger = mockk<ObserveLogger>(relaxed = true)

    /** Leaf carrying an explicit `ldMask` tag, so it always emits exactly one mask. */
    private fun maskedLeaf(): View = mockk<View>(relaxed = true).also {
        every { it.isShown } returns true
        every { it.alpha } returns 1f
        every { it.getTag(any()) } returns true
        every { it.width } returns 10
        every { it.height } returns 10
        every { it.id } returns View.NO_ID
    }

    private fun group(vararg children: View): ViewGroup = mockk<ViewGroup>(relaxed = true).also {
        every { it.isShown } returns true
        every { it.alpha } returns 1f
        every { it.getTag(any()) } returns null
        every { it.width } returns 100
        every { it.height } returns 100
        every { it.id } returns View.NO_ID
        every { it.childCount } returns children.size
        children.forEachIndexed { i, child -> every { it.getChildAt(i) } returns child }
    }

    private fun collect(
        root: View,
        stretchOf: (View) -> StretchDisplacement?
    ): List<Mask> = MaskCollector(logger, stretchOf)
        .collectMasks(root, emptyList(), emptyList(), emptyList())

    @Test
    fun `grows the masks under a stretched container and leaves the rest alone`() {
        val stretchedLeaf = maskedLeaf()
        val scroller = group(stretchedLeaf)
        val untouchedLeaf = maskedLeaf()
        val root = group(scroller, untouchedLeaf)

        val masks = collect(root) { view ->
            if (view === scroller) StretchDisplacement(dx = 4f, dy = 8f) else null
        }

        assertEquals(2, masks.size)
        val stretched = masks[0]
        assertEquals(-4f, stretched.rect.left)
        assertEquals(-8f, stretched.rect.top)
        assertEquals(4f, stretched.rect.right)
        assertEquals(8f, stretched.rect.bottom)

        val untouched = masks[1]
        assertEquals(0f, untouched.rect.left)
        assertEquals(0f, untouched.rect.top)
        assertEquals(0f, untouched.rect.right)
        assertEquals(0f, untouched.rect.bottom)
    }

    @Test
    fun `leaves masks alone when nothing is stretched`() {
        val leaf = maskedLeaf()
        val root = group(leaf)

        val masks = collect(root) { null }

        assertEquals(1, masks.size)
        assertEquals(0f, masks[0].rect.left)
        assertEquals(0f, masks[0].rect.top)
        assertEquals(0f, masks[0].rect.right)
        assertEquals(0f, masks[0].rect.bottom)
    }

    @Test
    fun `nested stretches compound on the masks they both cover`() {
        val leaf = maskedLeaf()
        val innerScroller = group(leaf)
        val outerScroller = group(innerScroller)

        val masks = collect(outerScroller) { StretchDisplacement(dx = 1f, dy = 2f) }

        assertEquals(1, masks.size)
        // Every view here reports a stretch and each grows what its own subtree emitted, so the
        // leaf's mask picks up all three.
        assertEquals(-3f, masks[0].rect.left)
        assertEquals(-6f, masks[0].rect.top)
    }
}
