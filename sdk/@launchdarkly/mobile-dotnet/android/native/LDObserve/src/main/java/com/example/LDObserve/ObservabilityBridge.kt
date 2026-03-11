package com.launchdarkly.LDNative

import android.app.Application
import com.example.LDObserve.BridgeLogger
import com.example.LDObserve.SystemOutBridgeLogger
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.replay.PrivacyProfile
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.LDAndroidLogging
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import com.launchdarkly.sdk.android.integrations.Plugin
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import java.util.Collections

public class LDObservabilityOptions {
    @JvmField var isEnabled: Boolean = true
    @JvmField var serviceName: String = ""
    @JvmField var serviceVersion: String = ""
    @JvmField var otlpEndpoint: String = ""
    @JvmField var backendUrl: String = ""
    @JvmField var contextFriendlyName: String? = null
    @JvmField var attributes: HashMap<String, Any?>? = null

    constructor()

    constructor(
        isEnabled: Boolean,
        serviceName: String,
        serviceVersion: String,
        otlpEndpoint: String,
        backendUrl: String,
        contextFriendlyName: String?,
        attributes: HashMap<String, Any?>? = null
    ) {
        this.isEnabled = isEnabled
        this.serviceName = serviceName
        this.serviceVersion = serviceVersion
        this.otlpEndpoint = otlpEndpoint
        this.backendUrl = backendUrl
        this.contextFriendlyName = contextFriendlyName
        this.attributes = attributes
    }
}

public class LDPrivacyOptions {
    @JvmField var maskTextInputs: Boolean = true
    @JvmField var maskWebViews: Boolean = false
    @JvmField var maskLabels: Boolean = false
    @JvmField var maskImages: Boolean = false
    @JvmField var minimumAlpha: Double = 0.02

    constructor()

    constructor(
        maskTextInputs: Boolean,
        maskWebViews: Boolean,
        maskLabels: Boolean,
        maskImages: Boolean,
        minimumAlpha: Double
    ) {
        this.maskTextInputs = maskTextInputs
        this.maskWebViews = maskWebViews
        this.maskLabels = maskLabels
        this.maskImages = maskImages
        this.minimumAlpha = minimumAlpha
    }
}

public class LDSessionReplayOptions {
    @JvmField var isEnabled: Boolean = true
    @JvmField var serviceName: String = ""
    @JvmField var privacy: LDPrivacyOptions = LDPrivacyOptions()

    constructor()

    constructor(
        isEnabled: Boolean,
        serviceName: String,
        privacy: LDPrivacyOptions
    ) {
        this.isEnabled = isEnabled
        this.serviceName = serviceName
        this.privacy = privacy
    }
}

internal fun buildResourceAttributes(source: HashMap<String, Any?>?): Attributes {
    if (source.isNullOrEmpty()) return Attributes.empty()
    val builder = Attributes.builder()
    source.forEach { (key, value) ->
        when (value) {
            is String -> builder.put(AttributeKey.stringKey(key), value)
            is Boolean -> builder.put(AttributeKey.booleanKey(key), value)
            is Long -> builder.put(AttributeKey.longKey(key), value)
            is Int -> builder.put(AttributeKey.longKey(key), value.toLong())
            is Double -> builder.put(AttributeKey.doubleKey(key), value)
            is Float -> builder.put(AttributeKey.doubleKey(key), value.toDouble())
            null -> {}
            else -> builder.put(AttributeKey.stringKey(key), value.toString())
        }
    }
    return builder.build()
}

public class ObservabilityBridge(
    private val logger: BridgeLogger = SystemOutBridgeLogger()
) {
    var isDebug: Boolean = true

    public fun getHookProxy(): RealObservabilityHookProxy? {
        val real = LDObserve.hookProxy ?: return null
        return RealObservabilityHookProxy(real)
    }

    public fun version(): String {
        return BuildConfig.OBSERVABILITY_SDK_VERSION
    }

    public fun recordLog(message: String, severity: Int) {
        // TODO: bridge to LDObserve.recordLog
    }

    public fun recordError(message: String, cause: String?) {
        // TODO: bridge to LDObserve.recordError
    }

    public fun recordMetric(name: String, value: Double) {
        // TODO: bridge to LDObserve.recordMetric
    }

    public fun recordCount(name: String, value: Double) {
        // TODO: bridge to LDObserve.recordCount
    }

    public fun recordIncr(name: String, value: Double) {
        // TODO: bridge to LDObserve.recordIncr
    }

    public fun recordHistogram(name: String, value: Double) {
        // TODO: bridge to LDObserve.recordHistogram
    }

    public fun recordUpDownCounter(name: String, value: Double) {
        // TODO: bridge to LDObserve.recordUpDownCounter
    }

    public fun start(
        app: Application,
        mobileKey: String,
        observability: LDObservabilityOptions,
        replay: LDSessionReplayOptions,
        observabilityVersion: String
    ) {
       // logger.debug("LD:ObservabilityBridge start called 7")

        val resourceAttributes = try {
            buildResourceAttributes(observability.attributes)
        } catch (t: Throwable) {
            printException("LD:resourceAttributes failed to build resourceAttributes", t)
            throw t
        }

        //logger.debug("LD:ObservabilityBridge resourceAttributes called")

        val nativeObservabilityOptions = try {
            com.launchdarkly.observability.api.ObservabilityOptions(
                enabled = observability.isEnabled,
                serviceName = observability.serviceName,
                serviceVersion = observabilityVersion,
                resourceAttributes = resourceAttributes,
                debug = false,
                otlpEndpoint = observability.otlpEndpoint,
                backendUrl = observability.backendUrl,
                tracesApi = com.launchdarkly.observability.api.ObservabilityOptions.TracesApi(includeErrors = true, includeSpans = true),
                metricsApi = com.launchdarkly.observability.api.ObservabilityOptions.MetricsApi.disabled(),
                instrumentations = com.launchdarkly.observability.api.ObservabilityOptions.Instrumentations(
                    crashReporting = false, launchTime = true, activityLifecycle = true
                ),
                logAdapter = LDAndroidLogging.adapter(),
            )
        } catch (t: Throwable) {
            printException("LD:ObservabilityBridge failed to build ObservabilityOptions", t)
            throw t
        }

        val observabilityPlugin = try {
            Observability(
                application = app,
                mobileKey = mobileKey,
                options = nativeObservabilityOptions
            )
        } catch (t: Throwable) {
            printException("LD:ObservabilityBridge failed to create Observability plugin", t)
            throw t
        }

        val nativeSessionReplayOptions = try {
            val privacy = replay.privacy
            com.launchdarkly.observability.replay.ReplayOptions(
                enabled = replay.isEnabled,
                privacyProfile = com.launchdarkly.observability.replay.PrivacyProfile(
                    maskTextInputs = privacy.maskTextInputs,
                    maskText = privacy.maskLabels,
                    maskImageViews = privacy.maskImages,
                    maskWebViews = privacy.maskWebViews
                )
            )
        } catch (t: Throwable) {
            printException("LD:ObservabilityBridge failed to build ReplayOptions", t)
            throw t
        }

        val sessionReplayPlugin = try {
            SessionReplay(options = nativeSessionReplayOptions)
        } catch (t: Throwable) {
            printException("LD:ObservabilityBridge failed to create SessionReplay plugin", t)
            throw t
        }

        logger.info(
            "LD:ObservabilityBridge Session replay enabled=${nativeSessionReplayOptions.enabled}, " +
                "backendUrl=${nativeObservabilityOptions.backendUrl}"
        )

        val ldConfig = try {
            LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
                .mobileKey(mobileKey)
                .offline(true)
                .plugins(
                    Components.plugins().setPlugins(
                        listOf(
                            observabilityPlugin,
                            sessionReplayPlugin
                        )
                    )
                )
                .build()
        } catch (t: Throwable) {
            printException("LD:ObservabilityBridge failed to build LDConfig", t)
            throw t
        }

        val context = try {
            LDContext.builder(ContextKind.DEFAULT, "maui-user-key")
                .anonymous(true)
                .build()
        } catch (t: Throwable) {
            printException("LD:ObservabilityBridge failed to build LDContext", t)
            throw t
        }

        try {
            LDClient.init(app, ldConfig, context)
            //logger.info("LD:ObservabilityBridge LDClient.init completed")
        } catch (t: Throwable) {
            printException("LD:ObservabilityBridge LDClient.init failed", t)
            throw t
        }
    }

    private fun printException(prefix: String, t: Throwable) {
        logger.error("$prefix ${t::class.java.name}: ${t.message}")
        val writer = java.io.StringWriter()
        val printWriter = java.io.PrintWriter(writer)
        t.printStackTrace(printWriter)
        printWriter.flush()
        logger.error(writer.toString())
    }
}

