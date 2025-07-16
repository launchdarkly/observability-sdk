package com.launchdarkly.observability.plugin

import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata

class Observability : Plugin() {
    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = "'@launchdarkly/observability-android'"
            // TODO: add getVersion to Android SDK if required
        }
    }

    override fun register(client: LDClient?, metadata: EnvironmentMetadata?) {
        TODO("Not yet implemented")
    }

}