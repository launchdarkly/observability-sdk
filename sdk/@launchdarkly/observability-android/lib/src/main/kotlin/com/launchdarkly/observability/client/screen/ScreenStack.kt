package com.launchdarkly.observability.client.screen

/**
 * Result of recording a screen into the [ScreenStack].
 *
 * Distinguishes a genuine screen change (which should emit telemetry) from a re-appearance of the
 * already-current screen (which should not), so callers don't emit duplicate `screen_view` spans or
 * `Navigate` events when an activity is merely resumed again (overlays, permission dialogs, the
 * recents switcher, etc.).
 */
sealed interface ScreenChange {
    /**
     * A real navigation: forward to a new screen, a pop-back to an earlier screen, or the first
     * screen. [previous] is the screen shown immediately before (or `null` if there was none).
     */
    data class Changed(val previous: String?) : ScreenChange

    /** The current top screen re-appeared; nothing navigated, so no telemetry should be emitted. */
    object Unchanged : ScreenChange
}

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
     * Records [name] as the current screen and returns a [ScreenChange] describing the transition.
     *
     * Returns [ScreenChange.Unchanged] when [name] is already the current top screen (a
     * re-appearance), and [ScreenChange.Changed] otherwise, carrying the previous screen name.
     */
    fun record(name: String): ScreenChange = synchronized(lock) {
        val top = stack.lastOrNull()

        // Re-appearance of the current screen: keep the stack and signal no change so callers
        // don't emit duplicate telemetry.
        if (top == name) {
            return ScreenChange.Unchanged
        }

        // Pop-back: the screen already exists lower in the stack. Trim everything above it so it
        // becomes the top again.
        val existingIndex = stack.lastIndexOf(name)
        if (existingIndex >= 0) {
            while (stack.size - 1 > existingIndex) {
                stack.removeLast()
            }
            return ScreenChange.Changed(stack.getOrNull(stack.size - 2))
        }

        // Forward navigation to a new screen.
        stack.addLast(name)
        return ScreenChange.Changed(top)
    }

    /** Clears the navigation history. */
    fun reset() = synchronized(lock) {
        stack.clear()
    }
}
