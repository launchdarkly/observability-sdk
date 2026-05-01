package com.launchdarkly.observability.replay.masking

import android.view.View
import android.view.ViewGroup
import com.launchdarkly.observability.context.ObserveLogger
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Behavioral tests for [MaskCollector]'s precedence rules. */
class MaskCollectorTest {
    private val logger = mockk<ObserveLogger>(relaxed = true)
    private val collector = MaskCollector(logger)

    /** Matcher that matches every target. Used to stand in for a "global config matched" signal. */
    private val matchAll = object : MaskMatcher {
        override fun isMatch(target: MaskTarget): Boolean = true
    }

    /**
     * Builds a [MaskMatcher] that matches only when the target wraps the exact [view] reference.
     * Useful for verifying inheritance: a matcher that fires on a parent but not its children
     * pins the propagation behavior.
     */
    private fun matchesOnly(view: View): MaskMatcher = object : MaskMatcher {
        override fun isMatch(target: MaskTarget): Boolean = target.view === view
    }

    /**
     * Builds a mocked leaf [View] with a controllable per-view masking signal. width/height are
     * positive so [NativeMaskTarget.mask] returns a non-null Mask, and `isShown=true` so
     * traversal visits the view. The masking signal is stubbed onto `view.getTag(any())` because
     * `NativeMaskTarget.hasLDMask` / `hasLDUnmask` look it up there via `R.id.ld_mask_tag`.
     *
     * @param ldMaskTag value returned from `view.getTag(R.id.ld_mask_tag)`. `true` = explicit
     *     mask (`ldMask()`), `false` = explicit unmask (`ldUnmask()`), `null` = no signal.
     */
    private fun mockLeaf(ldMaskTag: Boolean? = null): View = mockk<View>(relaxed = true).also {
        every { it.isShown } returns true
        every { it.getTag(any()) } returns ldMaskTag
        every { it.width } returns 10
        every { it.height } returns 10
        every { it.id } returns View.NO_ID
    }

    /**
     * Builds a mocked [ViewGroup] containing the given [children], plus the same per-view stubs
     * as [mockLeaf].
     *
     * @param children child views in declaration order; will be returned by `getChildAt(i)`.
     * @param ldMaskTag value returned from `view.getTag(R.id.ld_mask_tag)`. See [mockLeaf].
     */
    private fun mockGroup(vararg children: View, ldMaskTag: Boolean? = null): ViewGroup =
        mockk<ViewGroup>(relaxed = true).also {
            every { it.isShown } returns true
            every { it.getTag(any()) } returns ldMaskTag
            every { it.width } returns 10
            every { it.height } returns 10
            every { it.id } returns View.NO_ID
            every { it.childCount } returns children.size
            children.forEachIndexed { i, c -> every { it.getChildAt(i) } returns c }
        }

    @Test
    fun `ancestor ldMask propagates to descendant`() {
        val child = mockLeaf()
        val parent = mockGroup(child, ldMaskTag = true)

        val masks = collector.collectMasks(parent, emptyList(), emptyList(), emptyList())

        // Parent emits a mask via its own hasLDMask; child emits one via inherited mask.
        assertEquals(2, masks.size)
    }

    @Test
    fun `descendant ldUnmask does not override ancestor ldMask`() {
        val child = mockLeaf(ldMaskTag = false)
        val parent = mockGroup(child, ldMaskTag = true)

        val masks = collector.collectMasks(parent, emptyList(), emptyList(), emptyList())

        // Ancestor mask wins; child's ldUnmask tag is ignored when an ancestor is explicitly masked.
        assertEquals(2, masks.size)
    }

    @Test
    fun `ancestor ldUnmask overrides global matcher on descendant`() {
        val child = mockLeaf()
        val parent = mockGroup(child, ldMaskTag = false)

        val masks = collector.collectMasks(parent, emptyList(), emptyList(), listOf(matchAll))

        // Inherited unmask suppresses the global match on the descendant; the parent itself is
        // also not masked because its own ldUnmask vetoes the global matcher.
        assertEquals(0, masks.size)
    }

    @Test
    fun `explicit mask matcher wins over ldUnmask on the same view`() {
        // Pins the "mask wins over unmask at the same level" rule. A real-world instance is a
        // view configured in `maskXMLViewIds` that also carries `ldUnmask()` — the explicit-mask
        // signal wins.
        val view = mockLeaf(ldMaskTag = false)

        val masks = collector.collectMasks(view, listOf(matchAll), emptyList(), emptyList())

        assertEquals(1, masks.size)
    }

    @Test
    fun `ldUnmask on one sibling subtree does not affect another`() {
        val unmaskedChild = mockLeaf()
        val unmaskedSubtree = mockGroup(unmaskedChild, ldMaskTag = false)
        val plainChild = mockLeaf()
        val plainSubtree = mockGroup(plainChild)
        val root = mockGroup(unmaskedSubtree, plainSubtree)

        val masks = collector.collectMasks(root, emptyList(), emptyList(), listOf(matchAll))

        // root + plainSubtree + plainChild all match the global matcher (3 masks). The
        // unmaskedSubtree branch carries an explicit unmask that propagates down to its child,
        // suppressing both (0 masks). Total: 3.
        assertEquals(3, masks.size)
    }

    @Test
    fun `view without explicit signal falls through to global matcher`() {
        val view = mockLeaf()

        val masks = collector.collectMasks(view, emptyList(), emptyList(), listOf(matchAll))

        assertEquals(1, masks.size)
    }

    @Test
    fun `explicit unmask matcher suppresses global match on the same view`() {
        val view = mockLeaf()

        // Run the matcher list against a view that matches both unmask and global; explicit
        // unmask should win.
        val masks = collector.collectMasks(view, emptyList(), listOf(matchAll), listOf(matchAll))

        // Explicit unmask vetoes the global match — no mask emitted.
        assertEquals(0, masks.size)
    }

    @Test
    fun `explicit unmask matcher on ancestor propagates to descendant`() {
        val child = mockLeaf()
        val parent = mockGroup(child)

        // Only the parent matches the explicit-unmask matcher; the child does not match it
        // directly, so any propagation to descendants must come from the precedence rules.
        val masks = collector.collectMasks(
            parent,
            emptyList(),
            listOf(matchesOnly(parent)),
            listOf(matchAll),
        )

        // Parent's explicit unmask propagates to the child, suppressing the child's global match.
        assertEquals(0, masks.size)
    }

    @Test
    fun `explicit mask matcher wins over explicit unmask matcher on the same view`() {
        val view = mockLeaf()

        // Both lists match the same view; the precedence order says mask wins on the same level.
        val masks = collector.collectMasks(view, listOf(matchAll), listOf(matchAll), emptyList())

        // Single mask emitted — the explicit mask matcher beats the explicit unmask matcher.
        assertEquals(1, masks.size)
    }
}
