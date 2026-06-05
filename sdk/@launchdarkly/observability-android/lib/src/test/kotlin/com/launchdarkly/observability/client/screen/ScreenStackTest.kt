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
    fun `reset clears history`() {
        val stack = ScreenStack()
        stack.record("Home")
        stack.record("Detail")
        stack.reset()
        assertNull(previousOf(stack.record("Fresh")))
    }
}
