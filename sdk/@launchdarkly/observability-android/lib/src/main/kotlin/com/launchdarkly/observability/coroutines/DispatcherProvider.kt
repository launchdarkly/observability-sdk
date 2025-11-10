package com.launchdarkly.observability.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

internal object DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

internal object DispatcherProviderHolder {
    @Volatile
    private var provider: DispatcherProvider = DefaultDispatcherProvider

    val current: DispatcherProvider
        get() = provider

    internal fun set(provider: DispatcherProvider) {
        this.provider = provider
    }

    internal fun reset() {
        provider = DefaultDispatcherProvider
    }
}
