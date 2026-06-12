package com.launchdarkly.observability.plugin

import android.app.Application
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.DEFAULT_DISTRO_ATTRIBUTES
import com.launchdarkly.observability.client.ObservabilityService
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.client.buildObservabilityResource
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
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
    var distroAttributes: Map<String, String> = DEFAULT_DISTRO_ATTRIBUTES
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
        if (mobileKey != sdkKey) {
            logger.warn("ObservabilityContext could not be initialized for sdkKey: $sdkKey")
            return
        }
        LDObserve.context = ObservabilityContext(
            sdkKey = sdkKey,
            options = options,
            application = application,
            logger = logger
        )
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        return Collections.singletonList(observabilityHook)
    }

    override fun onPluginsReady(result: RegistrationCompleteResult?, metadata: EnvironmentMetadata?) {
        val sdkKey = metadata?.credential ?: ""

        if (client == null) {
            logger.error("Observability could not be initialized: LDClient is null in onPluginsReady")
            return
        }
        if (mobileKey != sdkKey) {
            logger.warn("Observability could not be initialized for sdkKey: $sdkKey")
            return
        }

        val resource = buildObservabilityResource(
            sdkKey = sdkKey,
            options = options,
            distroAttributes = distroAttributes,
            applicationId = metadata?.applicationInfo?.applicationId,
            applicationVersion = metadata?.applicationInfo?.applicationVersion,
            sdkVersion = composeLaunchDarklySdkVersion(metadata),
        )
        LDObserve.context?.resourceAttributes = resource.attributes

        val observabilityService = ObservabilityService(
            application, sdkKey, resource, logger, options,
        )
        observabilityClient = observabilityService
        LDObserve.context?.sessionManager = observabilityService.sessionManager
        LDObserve.context?.userInteractionManager = observabilityService.userInteractionManager
        LDObserve.context?.screenViewFlow = observabilityService.screenViewFlow
        LDObserve.context?.screenViewManager = observabilityService.screenViewManager
        LDObserve.context?.trackFlow = observabilityService.trackFlow
        LDObserve.context?.appLifecycleFlow = observabilityService.appLifecycleFlow
        LDObserve.context?.appLaunchSignal = observabilityService.appLaunchSignal
        LDObserve.init(observabilityService)

        observabilityHook.delegate = observabilityService.hookExporter
    }

    /**
     * Combines `EnvironmentMetadata.sdkMetadata.{name, version}` into the single
     * `launchdarkly.sdk.version` attribute value (`"$name/$version"`). Returns `null` if
     * either piece is missing, in which case [buildObservabilityResource] omits the attribute.
     */
    private fun composeLaunchDarklySdkVersion(metadata: EnvironmentMetadata?): String? {
        val sdk = metadata?.sdkMetadata ?: return null
        val name = sdk.name ?: return null
        val version = sdk.version ?: return null
        return "$name/$version"
    }

    fun getTelemetryInspector(): TelemetryInspector? {
        return options.telemetryInspector
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/observability-android"
    }
}
