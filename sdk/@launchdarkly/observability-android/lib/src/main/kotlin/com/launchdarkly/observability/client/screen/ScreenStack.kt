package com.launchdarkly.observability.client.screen

/**
 * Thread-safe navigation history used to resolve `event.previous_screen`.
 *
 * Mirrors the iOS screen stack: handles sequential navigation, re-appearance of the current
 * screen, and "pop-back" (returning to a screen already lower in the stack).
 */
class ScreenStack {
    private val lock = Any()
    private val stack = ArrayDeque<String>()

    /**
     * Records [name] as the current screen and returns the name of the screen shown immediately
     * before it (or `null` if there was none).
     */
    fun record(name: String): String? = synchronized(lock) {
        val top = stack.lastOrNull()

        // Re-appearance of the current screen: keep the stack, previous is the entry below top.
        if (top == name) {
            return stack.getOrNull(stack.size - 2)
        }

        // Pop-back: the screen already exists lower in the stack. Trim everything above it so it
        // becomes the top again.
        val existingIndex = stack.lastIndexOf(name)
        if (existingIndex >= 0) {
            while (stack.size - 1 > existingIndex) {
                stack.removeLast()
            }
            return stack.getOrNull(stack.size - 2)
        }

        // Forward navigation to a new screen.
        stack.addLast(name)
        return top
    }

    /** Clears the navigation history. */
    fun reset() = synchronized(lock) {
        stack.clear()
    }
}
