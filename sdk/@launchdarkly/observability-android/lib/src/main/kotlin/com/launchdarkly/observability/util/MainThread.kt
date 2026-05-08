package com.launchdarkly.observability.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch

/**
 * Main-thread helpers shared by the SDK.
 *
 * Several pieces of OpenTelemetry / Android instrumentation can only be installed on the UI thread
 * (e.g. lifecycle observers, view-tree listeners). These helpers centralise the looper check so we
 * don't repeat `Looper.myLooper() == Looper.getMainLooper()` everywhere.
 */

/** Returns `true` when the calling thread is the Android main (UI) looper thread. */
internal fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

/**
 * Throws [IllegalStateException] (via [check]) when invoked from a non-main thread.
 *
 * Use this at the top of methods that *must* run on the main thread but cannot reasonably
 * dispatch themselves there (e.g. constructors / `init` blocks).
 *
 * @param lazyMessage Builds the exception message. Only invoked on the failure path, so it's
 *                    safe to compose context-rich messages here.
 */
internal inline fun requireMainThread(
    lazyMessage: () -> Any = { "Must be called on the main thread" }
) {
    check(isMainThread(), lazyMessage)
}

/**
 * Executes [block] on the Android main thread.
 *
 * - If the caller is already on the main thread, [block] runs synchronously, in-place.
 * - Otherwise [block] is posted to the main [Looper] and the calling thread blocks on a
 *   [CountDownLatch] until it completes, preserving the caller's synchronous expectations.
 *
 * Any exception thrown by [block] is captured and re-raised on the calling thread, so failures
 * surface to the original caller rather than being silently swallowed by the main looper.
 *
 * Caveat: if the calling thread holds a lock the main thread is waiting on, this will deadlock.
 * Callers that may run from arbitrary background threads should ensure they don't hold UI-thread
 * dependencies while invoking this. If you don't actually need to wait for [block] to finish
 * (the typical case for "kick off main-thread initialization"), use [postOnMainThread] instead
 * to sidestep the deadlock hazard entirely.
 */
internal fun runOnMainThread(block: () -> Unit) {
    if (isMainThread()) {
        block()
        return
    }
    val latch = CountDownLatch(1)
    var thrown: Throwable? = null
    Handler(Looper.getMainLooper()).post {
        try {
            block()
        } catch (t: Throwable) {
            thrown = t
        } finally {
            latch.countDown()
        }
    }
    latch.await()
    thrown?.let { throw it }
}

/**
 * Schedules [block] to run on the Android main thread without blocking the caller.
 *
 * Always posts via [Handler] — even when invoked from the main thread — so the caller's frame
 * returns immediately and [block] runs on a subsequent looper turn. Use this for fire-and-forget
 * main-thread work where the caller doesn't need to observe completion (e.g. SDK initialization
 * dispatched from a constructor / bridge entry point).
 *
 * Unlike [runOnMainThread] this can never deadlock, because the caller never waits.
 *
 * Exceptions thrown by [block] propagate on the main looper just like any other posted runnable
 * (i.e. they crash the process). Wrap [block] internally if you need to swallow or report them.
 */
internal fun postOnMainThread(block: () -> Unit) {
    Handler(Looper.getMainLooper()).post(block)
}
