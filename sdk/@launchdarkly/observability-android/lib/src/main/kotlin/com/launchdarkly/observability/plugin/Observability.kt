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

        val resourceBuilder = Resource.getDefault().toBuilder()
        resourceBuilder.put("service.name", "observability-android") // TODO: allow this to be set via config
        resourceBuilder.put("service.version", "1.0.0") // TODO: allow this to be set via config
        resourceBuilder.put("highlight.project_id", sdkKey)

        metadata?.applicationInfo?.applicationId?.let {
            resourceBuilder.put("launchdarkly.application.id", it)
        }

        metadata?.applicationInfo?.applicationVersion?.let {
            resourceBuilder.put("launchdarkly.application.version", it)
        }

        metadata?.sdkMetadata?.name.let { sdkName ->
            metadata?.sdkMetadata?.version.let { sdkVersion ->
                resourceBuilder.put("launchdarkly.sdk.version", "$sdkName/$sdkVersion")
            }
        }

        val observabilityClient = ObservabilityClient(sdkKey, client, resourceBuilder.build())
        LDObserve.init(observabilityClient)
    }
}
