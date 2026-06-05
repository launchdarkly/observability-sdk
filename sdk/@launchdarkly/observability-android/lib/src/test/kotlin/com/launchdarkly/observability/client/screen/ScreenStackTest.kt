package com.launchdarkly.observability.client.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScreenStackTest {
    /** Asserts [change] is a [ScreenChange.Changed] and returns its `previous` value. */
    private fun previousOf(change: ScreenChange): String? {
        check(change is ScreenChange.Changed) { "expected Changed but was $change" }
        return change.previous
    }

    @Test
    fun `first screen has no previous`() {
        val stack = ScreenStack()
        assertNull(previousOf(stack.record("Home")))
    }

    @Test
    fun `sequential navigation reports the prior screen`() {
        val stack = ScreenStack()
        assertNull(previousOf(stack.record("Home")))
        assertEquals("Home", previousOf(stack.record("List")))
        assertEquals("List", previousOf(stack.record("Detail")))
    }

    @Test
    fun `re-appearance of current screen is unchanged`() {
        val stack = ScreenStack()
        stack.record("Home")
        assertEquals("Home", previousOf(stack.record("Detail")))
        // Detail re-appears (e.g. activity resumed again); no navigation occurred.
        assertEquals(ScreenChange.Unchanged, stack.record("Detail"))
    }

    @Test
    fun `re-appearance does not corrupt subsequent navigation`() {
        val stack = ScreenStack()
        stack.record("Home")
        stack.record("Detail")
        // Re-appearance is dropped, but the stack is intact: forward nav still reports Detail.
        assertEquals(ScreenChange.Unchanged, stack.record("Detail"))
        assertEquals("Detail", previousOf(stack.record("Settings")))
    }

    @Test
    fun `pop-back resolves the screen below the target`() {
        val stack = ScreenStack()
        stack.record("Home")
        stack.record("List")
        stack.record("Detail")
        // Navigate back to List; previous is Home, and the stack is trimmed above List.
        assertEquals("Home", previousOf(stack.record("List")))
        // Going forward again to Detail now reports List as previous.
        assertEquals("List", previousOf(stack.record("Detail")))
    }

    @Test
    fun `same name with distinct ids are separate screens`() {
        val stack = ScreenStack()
        assertNull(previousOf(stack.record("Detail", id = "item-1")))
        // Same display name but a different id is a real navigation, not a re-appearance.
        assertEquals("Detail", previousOf(stack.record("Detail", id = "item-2")))
    }

    @Test
    fun `re-appearance keyed by id is unchanged`() {
        val stack = ScreenStack()
        stack.record("Home", id = "home")
        assertEquals("Home", previousOf(stack.record("Detail", id = "item-1")))
        // The same id re-appears (e.g. activity resumed again); no navigation occurred.
        assertEquals(ScreenChange.Unchanged, stack.record("Detail", id = "item-1"))
    }

    @Test
    fun `pop-back matches by id`() {
        val stack = ScreenStack()
        stack.record("Detail", id = "item-1")
        stack.record("Detail", id = "item-2")
        stack.record("Detail", id = "item-3")
        // Returning to item-1 trims everything above it; previous is null (item-1 is the root).
        assertNull(previousOf(stack.record("Detail", id = "item-1")))
        // Going forward again reports item-1's name as previous.
        assertEquals("Detail", previousOf(stack.record("Detail", id = "item-4")))
    }

    @Test
    fun `reset clears history`() {
        val stack = ScreenStack()
        stack.record("Home")
        stack.record("Detail")
        stack.reset()
        assertNull(previousOf(stack.record("Fresh")))
    }
}
