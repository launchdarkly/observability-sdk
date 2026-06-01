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
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
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

        observabilityHook.delegate = observabilityService.hookExporter

        // Publish the SDK metadata that should be spread on every `launchdarkly.track` span.
        // Mirrors the JS reference `metaAttrs` (see
        // `sdk/highlight-run/src/plugins/observe.ts:99-113`). We populate this here — once
        // the LDClient `EnvironmentMetadata` is available — rather than at exporter construction
        // because `sdk.name/version`, `clientSideId`, and the application identifiers all live
        // on `metadata` and are not stable until `onPluginsReady`.
        //
        // IMPORTANT: this MUST run BEFORE `LDObserve.init(observabilityService)`. Once `init` is
        // called, the service is published to the [LDObserve] companion and any thread can
        // observe it via `LDObserve.track(...)`. If we reversed the order, that early track call
        // could land in `hookExporter.track(...)` with an empty `metaAttributes`, producing a
        // `launchdarkly.track` span missing `telemetry.sdk.*` / `feature_flag.set.id` /
        // `launchdarkly.application.*`. Mutating the (not-yet-published) exporter here is safe —
        // we still hold the only reference via `observabilityService`.
        observabilityService.hookExporter.setMetaAttributes(buildMetaAttributes(metadata, sdkKey))

        LDObserve.init(observabilityService)
    }

    /**
     * Builds the SDK-metadata `Attributes` spread on every `launchdarkly.track` span.
     *
     * Attribute keys mirror the JS reference (see
     * `sdk/highlight-run/src/integrations/launchdarkly/index.ts`):
     * `telemetry.sdk.{name,version}`, `feature_flag.set.id`, `feature_flag.provider.name`,
     * `launchdarkly.application.{id,version}`. Missing pieces (e.g. `application` not set
     * on the LDConfig) are simply omitted — same JS behavior.
     */
    private fun buildMetaAttributes(
        metadata: EnvironmentMetadata?,
        sdkKey: String,
    ): Attributes {
        val builder = Attributes.builder()
        metadata?.sdkMetadata?.let { sdk ->
            sdk.name?.let { builder.put(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_TELEMETRY_SDK_NAME), it) }
            sdk.version?.let { builder.put(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_TELEMETRY_SDK_VERSION), it) }
        }
        // The mobile-key/client-side-id used to identify the LD environment.
        // Matches the JS reference's use of `metadata.clientSideId`.
        builder.put(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_FEATURE_FLAG_SET_ID), sdkKey)
        builder.put(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_FEATURE_FLAG_PROVIDER_NAME), ObservabilityHookExporter.PROVIDER_NAME)
        metadata?.applicationInfo?.applicationId?.let {
            builder.put(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_LD_APPLICATION_ID), it)
        }
        metadata?.applicationInfo?.applicationVersion?.let {
            builder.put(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_LD_APPLICATION_VERSION), it)
        }
        return builder.build()
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
