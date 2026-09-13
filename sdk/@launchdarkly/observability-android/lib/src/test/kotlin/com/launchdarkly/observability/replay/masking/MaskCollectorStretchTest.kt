package com.launchdarkly.observability.replay.masking

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import com.launchdarkly.observability.context.ObserveLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

    /**
     * Leaf that paints a solid opaque fill, so it absorbs the masks collected before it. Emits no
     * mask of its own. Every rect is all-zero here, so it covers any axis-aligned mask.
     */
    private fun opaqueLeaf(): View = mockk<View>(relaxed = true).also {
        every { it.isShown } returns true
        every { it.alpha } returns 1f
        every { it.getTag(any()) } returns null
        every { it.width } returns 100
        every { it.height } returns 100
        every { it.id } returns View.NO_ID
        every { it.background } returns mockk<ColorDrawable>(relaxed = true) {
            every { alpha } returns 255
        }
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
    fun `grows the subtree's masks even when culling drops an earlier one`() {
        // An opaque child inside the scroller absorbs the mask that the leaf behind it emitted,
        // shortening the list mid-walk. The scroller's own mask must still grow.
        val culledLeaf = maskedLeaf()
        val stretchedLeaf = maskedLeaf()
        // Visited before the leaf, so the mask it absorbs is the one from outside the scroller.
        val scroller = group(opaqueLeaf(), stretchedLeaf)
        val root = group(culledLeaf, scroller)

        mockkStatic(Color::class)
        try {
            every { Color.alpha(any()) } returns 255

            val masks = collect(root) { view ->
                if (view === scroller) StretchDisplacement(dx = 4f, dy = 8f) else null
            }

            assertEquals(1, masks.size)
            assertEquals(-4f, masks[0].rect.left)
            assertEquals(-8f, masks[0].rect.top)
            assertEquals(4f, masks[0].rect.right)
            assertEquals(8f, masks[0].rect.bottom)
        } finally {
            unmockkStatic(Color::class)
        }
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
