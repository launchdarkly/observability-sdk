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
import com.launchdarkly.observability.client.readInjectedSymbolsId
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.DedupingHook
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import java.util.Collections

/**
 * This Observability class is a plugin implementation for recording observability data such as metrics, logs, errors, and traces.
 *
 * Prefer [LDObserve.init] to register it against an initialized [LDClient]:
 *
 * ```
 * LDObserve.init(
 *     application = this@BaseApplication,
 *     ldClient = LDClient.get(),
 *     ldContext = ldObserveContext,
 * )
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
 * @param customSessionId Optional session id to adopt instead of generating one. Lets the native
 *   instance share a single `session.id` with another LaunchDarkly SDK on the device (e.g. the
 *   JavaScript SDK in a React Native app). When null, a session id is generated automatically.
 * @param expectedMobileKey The mobile key of the environment this plugin should initialize for, or
 *   `null` to initialize for whichever environment it is registered with.
 */
class Observability internal constructor(
    private val application: Application,
    private val options: ObservabilityOptions,
    private val customSessionId: String?,
    private val expectedMobileKey: String?,
) : Plugin() {
    var distroAttributes: Map<String, String> = DEFAULT_DISTRO_ATTRIBUTES
    private val logger: ObserveLogger
    private val observabilityHook = ObservabilityHook()

    init {
        logger = ObserveLogger.build(options.logAdapter, options.loggerName, options.debug)
    }

    /**
     * Creates a plugin to pass to [com.launchdarkly.sdk.android.LDConfig.Builder.plugins], which
     * initializes only for the environment identified by [mobileKey].
     */
    @Deprecated(
        "Pass an initialized LDClient to LDObserve.init instead of adding this plugin to LDConfig."
    )
    constructor(
        application: Application,
        mobileKey: String,
        options: ObservabilityOptions = ObservabilityOptions(), // new instance has reasonable defaults
        customSessionId: String? = null,
    ) : this(application, options, customSessionId, expectedMobileKey = mobileKey)

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = PLUGIN_NAME
            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    /**
     * Installs observability for [client]'s environment: builds the pipeline and publishes it, so
     * the recording API starts working and the hook handed to [getHooks] has somewhere to report.
     *
     * All of it happens here rather than split across [onPluginsReady] so both initialization paths
     * install the same way. Nothing here depends on the other plugins having registered, and Session
     * Replay reads the context this publishes, so it must be complete by the time the next plugin
     * registers.
     */
    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        val sdkKey = metadata?.credential ?: ""
        if (!isForEnvironment(sdkKey)) {
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

        val observabilityService = ObservabilityService(
            application, sdkKey, resource, logger, options, customSessionId,
        )

        // Wired before publication so no reader can observe a half-built context.
        val obsContext = ObservabilityContext(
            sdkKey = sdkKey,
            options = options,
            application = application,
            logger = logger
        )
        obsContext.resourceAttributes = resource.attributes
        obsContext.sessionManager = observabilityService.sessionManager
        obsContext.userInteractionManager = observabilityService.userInteractionManager
        obsContext.screenViewFlow = observabilityService.screenViewFlow
        obsContext.screenViewManager = observabilityService.screenViewManager
        obsContext.trackFlow = observabilityService.trackFlow
        obsContext.identifyFlow = observabilityService.identifyFlow
        obsContext.appLifecycleFlow = observabilityService.appLifecycleFlow
        obsContext.appLaunchSignal = observabilityService.appLaunchSignal

        observabilityHook.delegate = observabilityService.hookExporter
        LDObserve.context = obsContext
        // Only this path has a client, so it is what distinguishes an installation that instruments
        // the flagging SDK from a standalone one.
        LDObserve.isFlagClientInitialized = true
        LDObserve.init(observabilityService)
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        // Deduplicate repeated identical evaluations (default 10-minute window).
        // Resets after identify or when the evaluation result changes.
        return Collections.singletonList(DedupingHook(observabilityHook))
    }

    /**
     * Whether this plugin should initialize for the environment identified by [sdkKey].
     *
     * A plugin configured on [com.launchdarkly.sdk.android.LDConfig] is handed to every
     * environment's client, so it names the one environment it belongs to and declines the rest.
     * A plugin registered against a single [LDClient] belongs to whichever environment that client
     * is for, so there is nothing to decline.
     */
    private fun isForEnvironment(sdkKey: String): Boolean =
        expectedMobileKey == null || expectedMobileKey == sdkKey

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
