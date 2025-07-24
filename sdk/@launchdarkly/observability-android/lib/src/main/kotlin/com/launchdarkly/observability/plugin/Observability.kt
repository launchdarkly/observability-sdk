package com.launchdarkly.observability.plugin

import ObservabilityClient
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import io.opentelemetry.sdk.resources.Resource

class Observability : Plugin() {
    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = "'@launchdarkly/observability-android'"
            // TODO: add getVersion to Android SDK if required
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        val sdkKey = metadata?.credential ?: ""
        val resource = Resource.getDefault()
        val observabilityClient = ObservabilityClient(sdkKey, client, resource)
        LDObserve.init(observabilityClient)
    }
}
