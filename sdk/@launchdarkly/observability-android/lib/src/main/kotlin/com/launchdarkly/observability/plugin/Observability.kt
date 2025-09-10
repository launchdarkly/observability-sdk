package com.launchdarkly.observability.plugin

import android.app.Application
import com.launchdarkly.logging.LDLogLevel
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.logging.Logs
import com.launchdarkly.observability.api.Options
import com.launchdarkly.observability.client.ObservabilityClient
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import io.opentelemetry.sdk.resources.Resource
import java.util.Collections

class Observability(
    private val application: Application,
    private val options: Options = Options() // new instance has reasonable defaults
) : Plugin() {
    private val logger: LDLogger
    private var observabilityClient: ObservabilityClient? = null

    init {
        val actualLogAdapter = Logs.level(options.logAdapter, if (options.debug) LDLogLevel.DEBUG else DEFAULT_LOG_LEVEL)
        logger = LDLogger.withAdapter(actualLogAdapter, options.loggerName)
    }

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
        resourceBuilder.putAll(options.resourceAttributes)

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

        observabilityClient = ObservabilityClient(application, sdkKey, resourceBuilder.build(), logger, options)
        observabilityClient?.let { LDObserve.init(it) }
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        return Collections.singletonList(
            EvalTracingHook(true, true)
        )
    }

    fun getTelemetryInspector(): TelemetryInspector? {
        return observabilityClient?.getTelemetryInspector()
    }

    companion object {
        val DEFAULT_LOG_LEVEL: LDLogLevel = LDLogLevel.INFO
    }
}
