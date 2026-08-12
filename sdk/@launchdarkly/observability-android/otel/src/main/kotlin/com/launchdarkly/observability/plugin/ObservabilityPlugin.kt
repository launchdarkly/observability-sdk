package com.launchdarkly.observability.plugin

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.client.ObservabilityInstrumenting
import com.launchdarkly.observability.client.ObservabilityService
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.client.buildObservabilityResource
import com.launchdarkly.observability.client.distroAttributes
import com.launchdarkly.observability.client.readInjectedSymbolsId
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
import java.util.Collections

/**
 * Shared LDClient plugin behaviour for both observability products.
 *
 * Everything here is common: building the resource, constructing the [ObservabilityService],
 * publishing it as the [LDObserve] delegate, and installing the flag-evaluation / identify /
 * afterTrack hooks. The products differ in exactly one respect — whether
 * [createInstrumentation] returns automatic instrumentation — which is the difference between
 * `Otel` and `Observability`.
 *
 * @param application The application instance.
 * @param mobileKey The primary mobile key used in LDConfig.
 * @param options The options for the plugin.
 * @param customSessionId Optional session id to adopt instead of generating one. Lets the native
 *   instance share a single `session.id` with another LaunchDarkly SDK on the device (e.g. the
 *   JavaScript SDK in a React Native app). When null, a session id is generated automatically.
 * @param pluginName Reported to LaunchDarkly as the plugin's identity.
 * @param distroName Reported as `telemetry.distro.name`, so telemetry says which product sent it.
 */
abstract class ObservabilityPlugin(
    private val application: Application,
    private val mobileKey: String,
    private val options: ObservabilityOptions,
    private val customSessionId: String?,
    private val pluginName: String,
    distroName: String,
) : Plugin() {
    var distroAttributes: Map<String, String> = distroAttributes(distroName)

    protected val logger: ObserveLogger =
        ObserveLogger.build(options.logAdapter, options.loggerName, options.debug)

    private val observabilityHook = ObservabilityHook()
    private var observabilityClient: ObservabilityService? = null
    private var client: LDClient? = null

    /**
     * Supplies the automatic instrumentation to install. The default installs none, which is what
     * makes the OTel-only product safe to run alongside another observability SDK.
     */
    protected open fun createInstrumentation(sdkKey: String): ObservabilityInstrumenting? = null

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = pluginName
            override fun getVersion(): String = com.launchdarkly.otel.BuildConfig.OBSERVABILITY_SDK_VERSION
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
            symbolsId = readInjectedSymbolsId(application),
        )
        LDObserve.context?.resourceAttributes = resource.attributes

        val observabilityService = ObservabilityService(
            application, sdkKey, resource, logger, options, customSessionId,
            instrumenting = createInstrumentation(sdkKey),
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
}
