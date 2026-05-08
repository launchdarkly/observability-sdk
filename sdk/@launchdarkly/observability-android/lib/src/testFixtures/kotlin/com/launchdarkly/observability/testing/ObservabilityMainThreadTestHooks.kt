package com.launchdarkly.observability.testing

import com.launchdarkly.observability.util.MainThreadExecutor
import com.launchdarkly.observability.util.MainThreadExecutorHolder

/**
 * Allows test modules to override the SDK's main-thread executor.
 *
 * The production executor delegates to Android's main `Looper`, which is unavailable in plain
 * JVM unit tests (calls to `Looper.myLooper()` throw "Method not mocked"). This hook installs
 * a synchronous executor that runs every block on the calling thread, so tests can exercise
 * code paths guarded by `requireMainThread` / `runOnMainThread` without setting up Robolectric
 * or `unitTests.returnDefaultValues = true`.
 *
 * Mirrors [ObservabilityDispatcherTestHooks] in spirit: visible only via the test fixtures
 * artifact, never reachable by SDK consumers.
 *
 * Typical usage:
 *
 * ```
 * @BeforeEach fun setUp() { ObservabilityMainThreadTestHooks.overrideWithSynchronous() }
 * @AfterEach  fun tearDown() { ObservabilityMainThreadTestHooks.reset() }
 * ```
 */
object ObservabilityMainThreadTestHooks {

    /**
     * Replaces the production main-thread executor with [SynchronousMainThreadExecutor].
     * Subsequent calls to `isMainThread()` return `true`; `runOnMainThread` and
     * `postOnMainThread` execute their block immediately on the calling thread.
     *
     * Always pair with [reset] in `@AfterEach` so the swap doesn't leak across tests.
     */
    fun overrideWithSynchronous() {
        MainThreadExecutorHolder.set(SynchronousMainThreadExecutor)
    }

    /** Restores the production [com.launchdarkly.observability.util.AndroidLooperMainThreadExecutor]. */
    fun reset() {
        MainThreadExecutorHolder.reset()
    }

    /**
     * Test executor that runs everything on the calling thread. Reports `isMainThread() = true`
     * so [com.launchdarkly.observability.util.requireMainThread] checks pass.
     */
    private object SynchronousMainThreadExecutor : MainThreadExecutor {
        override fun isMainThread(): Boolean = true
        override fun runOnMainThread(block: () -> Unit) = block()
        override fun postOnMainThread(block: () -> Unit) = block()
    }
}
