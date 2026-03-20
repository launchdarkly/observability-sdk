package com.launchdarkly.LDNative

import com.launchdarkly.observability.bridge.LDObserveBridge as SdkLDObserveBridge

class LDObserveBridgeAdapter {
    companion object {
        @JvmStatic
        fun getObservabilityHookProxy(): RealObservabilityHookProxy? {
            val proxy = SdkLDObserveBridge.getObservabilityHookProxy() ?: return null
            return RealObservabilityHookProxy(proxy)
        }
    }
}
