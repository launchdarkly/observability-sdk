package com.launchdarkly.observability.plugin

import android.app.Application
import com.launchdarkly.observability.devlog.ObserveLogger
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityService
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.Resource
import java.util.Collections

/**
 * This Observability class is a plugin implementation for recording observability data such as metrics, logs, errors, and traces.
 * Provide the plugin to the LaunchDarkly Android Client SDK to enable observability.
 *
 * ```
 * val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
 *     .mobileKey(LAUNCHDARKLY_MOBILE_KEY)
 *     .plugins(
 *         Components.plugins().setPlugins(
 *             listOf(
 *                 Observability(this@BaseApplication)
 *             )
 *         )
 *     )
 *     .build()
 * ```
 *
 * Later after initialization you can use [LDObserve] to record observability data.
 *
 * ```
 * LDObserve.recordMetric(metric)
 * LDObserve.recordLog(message, severity, attributes)
 * LDObserve.recordError(error, attributes)
 * LDObserve.startSpan(name, attributes)
 * ```
 *
 * @param application The application instance.
 * @param options The options for the plugin.
 * @param mobileKey The primary mobile key used in LDConfig.
 */
class Observability(
    private val application: Application,
    private val mobileKey: String,
    private val options: ObservabilityOptions = ObservabilityOptions() // new instance has reasonable defaults
) : Plugin() {
    var distroAttributes: Map<String, String> = mapOf(
        "telemetry.distro.name" to SDK_NAME,
        "telemetry.distro.version" to BuildConfig.OBSERVABILITY_SDK_VERSION
    )
    private val logger: ObserveLogger
    private val observabilityHook = ObservabilityHook()
    private var observabilityClient: ObservabilityService? = null
    private var client: LDClient? = null

    init {
        logger = ObserveLogger.build(options.logAdapter, options.loggerName, options.debug)
    }

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = PLUGIN_NAME
            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        this.client = client
        val sdkKey = metadata?.credential ?: ""
        if (mobileKey == sdkKey) {
            LDObserve.context = ObservabilityContext(
                sdkKey = sdkKey,
                options = options,
                application = application,
                logger = logger
            )
        } else {
            logger.warn("ObservabilityContext could not be initialized for sdkKey: $sdkKey")
        }
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        return Collections.singletonList(observabilityHook)
    }

    override fun onPluginsReady(result: RegistrationCompleteResult?, metadata: EnvironmentMetadata?) {
        val sdkKey = metadata?.credential ?: ""

        client?.let { lDClient ->
            if (mobileKey == sdkKey) {
                val attributes = Attributes.builder()
                Resource.getDefault().attributes.forEach { key, value ->
                    if (key.key != "service.name") {
                        @Suppress("UNCHECKED_CAST")
                        attributes.put(key as AttributeKey<Any>, value)
                    }
                }
                attributes.put("highlight.project_id", sdkKey)
                distroAttributes.forEach { (key, value) ->
                    attributes.put(AttributeKey.stringKey(key), value)
                }
                attributes.putAll(options.resourceAttributes)

                metadata?.applicationInfo?.applicationId?.let {
                    attributes.put("launchdarkly.application.id", it)
                }

                metadata?.applicationInfo?.applicationVersion?.let {
                    attributes.put("launchdarkly.application.version", it)
                }

                metadata?.sdkMetadata?.name?.let { sdkName ->
                    metadata.sdkMetadata?.version?.let { sdkVersion ->
                        attributes.put("launchdarkly.sdk.version", "$sdkName/$sdkVersion")
                    }
                }

                val builtResource = Resource.create(attributes.build())
                LDObserve.context?.resourceAttributes = builtResource.attributes

                val client = ObservabilityService(
                    application, sdkKey, builtResource, logger, options,
                )
                observabilityClient = client
                LDObserve.context?.sessionManager = client.sessionManager
                LDObserve.init(client)

                observabilityHook.delegate = client.hookExporter
            } else {
                logger.warn("Observability could not be initialized for sdkKey: $sdkKey")
            }
        }
    }

    fun getTelemetryInspector(): TelemetryInspector? {
        return options.telemetryInspector
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/observability-android"
        const val SDK_NAME = "launchdarkly-observability-android"
    }
}
