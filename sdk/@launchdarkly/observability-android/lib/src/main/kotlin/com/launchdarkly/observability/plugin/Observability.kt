package com.launchdarkly.observability.plugin

import android.app.Application
import com.launchdarkly.observability.client.ObservabilityClient
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.observability.api.Options
import io.opentelemetry.sdk.resources.Resource
import java.util.Collections

class Observability(
    private val application: Application,
    private val options: Options = Options() // new instance has reasonable defaults
) : Plugin() {
    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = "@launchdarkly/observability-android"

            // Uncomment once metadata supports version
//            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        val sdkKey = metadata?.credential ?: ""

        val resourceBuilder = Resource.getDefault().toBuilder()
        resourceBuilder.put("service.name", options.serviceName)
        resourceBuilder.put("service.version", options.serviceVersion)
        resourceBuilder.put("highlight.project_id", sdkKey)

        metadata?.applicationInfo?.applicationId?.let {
            resourceBuilder.put("launchdarkly.application.id", it)
        }

        metadata?.applicationInfo?.applicationVersion?.let {
            resourceBuilder.put("launchdarkly.application.version", it)
        }

        metadata?.sdkMetadata?.name?.let { sdkName ->
            metadata.sdkMetadata?.version?.let { sdkVersion ->
                resourceBuilder.put("launchdarkly.sdk.version", "$sdkName/$sdkVersion")
            }
        }

        val observabilityClient = ObservabilityClient(application, sdkKey, resourceBuilder.build(), options)
        LDObserve.init(observabilityClient)
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        return Collections.singletonList(
            EvalTracingHook(true, true)
        )
    }
}
