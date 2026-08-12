package com.launchdarkly.observability.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@com.launchdarkly.observability.InternalObservabilityApi
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

@com.launchdarkly.observability.InternalObservabilityApi
object DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

@com.launchdarkly.observability.InternalObservabilityApi
object DispatcherProviderHolder {
    @Volatile
    private var provider: DispatcherProvider = DefaultDispatcherProvider

    val current: DispatcherProvider
        get() = provider

    fun set(provider: DispatcherProvider) {
        this.provider = provider
    }

    fun reset() {
        provider = DefaultDispatcherProvider
    }
}
