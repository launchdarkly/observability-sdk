package com.launchdarkly.observability.client.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScreenStackTest {
    @Test
    fun `first screen has no previous`() {
        val stack = ScreenStack()
        assertNull(stack.record("Home"))
    }

    @Test
    fun `sequential navigation reports the prior screen`() {
        val stack = ScreenStack()
        assertNull(stack.record("Home"))
        assertEquals("Home", stack.record("List"))
        assertEquals("List", stack.record("Detail"))
    }

    @Test
    fun `re-appearance of current screen keeps the original previous`() {
        val stack = ScreenStack()
        stack.record("Home")
        assertEquals("Home", stack.record("Detail"))
        // Detail re-appears (e.g. configuration change); previous should still be Home.
        assertEquals("Home", stack.record("Detail"))
    }

    @Test
    fun `pop-back resolves the screen below the target`() {
        val stack = ScreenStack()
        stack.record("Home")
        stack.record("List")
        stack.record("Detail")
        // Navigate back to List; previous is Home, and the stack is trimmed above List.
        assertEquals("Home", stack.record("List"))
        // Going forward again to Detail now reports List as previous.
        assertEquals("List", stack.record("Detail"))
    }

    @Test
    fun `reset clears history`() {
        val stack = ScreenStack()
        stack.record("Home")
        stack.record("Detail")
        stack.reset()
        assertNull(stack.record("Fresh"))
    }
}
