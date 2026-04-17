package com.launchdarkly.observability.bridge

import com.launchdarkly.observability.sdk.LDObserveInternal

object LDObserveBridge {
    fun getObservabilityHookProxy(): ObservabilityHookProxy? {
        return LDObserveInternal.observabilityClient?.let { ObservabilityHookProxy(it.hookExporter) }
    }

    fun getKotlinTracer(): KotlinTracer? {
        return LDObserveInternal.observabilityClient?.let { KotlinTracer(it.getTracer()) }
    }

    fun getKotlinLogger(): KotlinLogger? {
        return LDObserveInternal.observabilityClient?.let {
            KotlinLogger(internalLogger = it.getLogger(), customerLogger = it)
        }
    }
}
