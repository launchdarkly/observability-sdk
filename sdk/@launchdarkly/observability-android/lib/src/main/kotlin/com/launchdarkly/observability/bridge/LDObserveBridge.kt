package com.launchdarkly.observability.bridge

import com.launchdarkly.observability.sdk.LDObserve

object LDObserveBridge {
    fun getObservabilityHookProxy(): ObservabilityHookProxy? {
        return LDObserve.observabilityClient?.let { ObservabilityHookProxy(it.hookExporter) }
    }
}
