package com.launchdarkly.observability.plugin

import android.app.Application
import com.launchdarkly.logging.LDLogLevel
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.logging.Logs
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityClient
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
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
    private val logger: LDLogger
    private var observabilityClient: ObservabilityClient? = null
    private var client: LDClient? = null

    init {
        val actualLogAdapter = Logs.level(options.logAdapter, if (options.debug) LDLogLevel.DEBUG else DEFAULT_LOG_LEVEL)
        logger = LDLogger.withAdapter(actualLogAdapter, options.loggerName)
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
        val exporter = ObservabilityHookExporter(
            withSpans = true,
            withValue = true,
            tracerProvider = { observabilityClient?.getTracer() }
        )
        LDObserve.hookProxy = ObservabilityHookProxy(exporter)
        return Collections.singletonList(ObservabilityHook(exporter))
    }

    override fun onPluginsReady(result: RegistrationCompleteResult?, metadata: EnvironmentMetadata?) {
        val sdkKey = metadata?.credential ?: ""

        client?.let { lDClient ->
            if (mobileKey == sdkKey) {
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

                val instrumentations = InstrumentationContributorManager.get(lDClient).flatMap { it.provideInstrumentations() }
                observabilityClient = ObservabilityClient(
                    application, sdkKey, resourceBuilder.build(), logger, options, instrumentations
                )
                observabilityClient?.let {
                    LDObserve.init(it)
                }
            } else {
                logger.warn("Observability could not be initialized for sdkKey: $sdkKey")
            }
        }
    }

    fun getTelemetryInspector(): TelemetryInspector? {
        return observabilityClient?.getTelemetryInspector()
    }

    companion object {
        val DEFAULT_LOG_LEVEL: LDLogLevel = LDLogLevel.INFO
        const val PLUGIN_NAME = "@launchdarkly/observability-android"
    }
}
