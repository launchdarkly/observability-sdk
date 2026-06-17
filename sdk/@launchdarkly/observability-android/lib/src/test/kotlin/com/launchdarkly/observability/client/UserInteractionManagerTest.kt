package com.launchdarkly.observability.client

import android.view.View
import android.view.ViewGroup
import com.launchdarkly.observability.R
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Tests for [UserInteractionManager.resolveLdId]'s native `ldId(...)` tag resolution. */
class UserInteractionManagerTest {
    private val manager = UserInteractionManager()

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
}
