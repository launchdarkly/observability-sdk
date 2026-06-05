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
 *
 * Screen identity is keyed on `screenId` when supplied, falling back to `name`. This keeps two
 * distinct screens that share a display name (e.g. a detail screen reused with per-item
 * `screenId`s) from being collapsed into one another, while `previous_screen` is still reported
 * using the human-readable name.
 */
class ScreenStack {
    /** A recorded screen: [key] is the identity for matching, [name] is reported as previous. */
    private data class Entry(val key: String, val name: String)

    private val lock = Any()
    private val stack = ArrayDeque<Entry>()

    /**
     * Records a screen appearance and returns a [ScreenChange] describing the transition.
     *
     * Identity is keyed on [id] when provided, otherwise [name]. Returns [ScreenChange.Unchanged]
     * when the same screen is already the current top (a re-appearance), and [ScreenChange.Changed]
     * otherwise, carrying the previous screen name.
     */
    fun record(name: String, id: String? = null): ScreenChange = synchronized(lock) {
        val key = id ?: name
        val top = stack.lastOrNull()

        // Re-appearance of the current screen: keep the stack and signal no change so callers
        // don't emit duplicate telemetry.
        if (top?.key == key) {
            return ScreenChange.Unchanged
        }

        // Pop-back: the screen already exists lower in the stack. Trim everything above it so it
        // becomes the top again.
        val existingIndex = stack.indexOfLast { it.key == key }
        if (existingIndex >= 0) {
            while (stack.size - 1 > existingIndex) {
                stack.removeLast()
            }
            return ScreenChange.Changed(stack.getOrNull(stack.size - 2)?.name)
        }

        // Forward navigation to a new screen.
        stack.addLast(Entry(key = key, name = name))
        return ScreenChange.Changed(top?.name)
    }

    /** Clears the navigation history. */
    fun reset() = synchronized(lock) {
        stack.clear()
    }
}
