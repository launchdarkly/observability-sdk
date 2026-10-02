package com.launchdarkly.LDNative

import com.launchdarkly.observability.bridge.LDObserveBridge as SdkLDObserveBridge

class LDObserveBridgeAdapter {
    companion object {
        @JvmStatic
        fun getObservabilityHookProxy(): RealObservabilityHookProxy? {
            val proxy = SdkLDObserveBridge.getObservabilityHookProxy() ?: return null
            return RealObservabilityHookProxy(proxy)
        }

        @JvmStatic
        fun getTracer(): RealTracer? {
            val tracer = SdkLDObserveBridge.getKotlinTracer() ?: return null
            return RealTracer(tracer)
        }

        @JvmStatic
        fun getLogger(): RealLogger? {
            val logger = SdkLDObserveBridge.getKotlinLogger() ?: return null
            return RealLogger(logger)
        }
    }
}
