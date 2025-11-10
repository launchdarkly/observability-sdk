package com.launchdarkly.observability.testing

import com.launchdarkly.observability.coroutines.DefaultDispatcherProvider
import com.launchdarkly.observability.coroutines.DispatcherProvider
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Allows test modules to override the coroutine dispatchers used by the SDK.
 *
 * This hooks directly into [DispatcherProviderHolder] but remains inaccessible to SDK consumers
 * because it is published only via the test fixtures artifact.
 */
object ObservabilityDispatcherTestHooks {

    /**
     * Overrides dispatchers used by the SDK. Any argument left null continues to use the default
     * value provided by the SDK.
     */
    fun overrideDispatchers(
        main: CoroutineDispatcher? = null,
        io: CoroutineDispatcher? = null,
        default: CoroutineDispatcher? = null,
        unconfined: CoroutineDispatcher? = null,
    ) {
        val fallback = DefaultDispatcherProvider
        DispatcherProviderHolder.set(
            object : DispatcherProvider {
                override val main = main ?: fallback.main
                override val io = io ?: fallback.io
                override val default = default ?: fallback.default
                override val unconfined = unconfined ?: fallback.unconfined
            }
        )
    }

    /**
     * Convenience override that routes every dispatcher to the same [CoroutineDispatcher].
     */
    fun overrideWith(dispatcher: CoroutineDispatcher) {
        overrideDispatchers(
            main = dispatcher,
            io = dispatcher,
            default = dispatcher,
            unconfined = dispatcher
        )
    }

    /**
     * Restores the SDK dispatchers to their defaults.
     */
    fun reset() {
        DispatcherProviderHolder.reset()
    }
}
