package com.launchdarkly.LDNative

import android.app.Application
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.api.ObservabilityOptions
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

    constructor()

    constructor(
        isEnabled: Boolean,
        serviceName: String,
        serviceVersion: String,
        otlpEndpoint: String,
        backendUrl: String,
        contextFriendlyName: String?
    ) {
        this.isEnabled = isEnabled
        this.serviceName = serviceName
        this.serviceVersion = serviceVersion
        this.otlpEndpoint = otlpEndpoint
        this.backendUrl = backendUrl
        this.contextFriendlyName = contextFriendlyName
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

public class ObservabilityBridge {
    private fun createObservabilityOptionsCompat(
        observability: LDObservabilityOptions,
        resourceAttributes: Attributes
    ): ObservabilityOptions {
        val tracesApi = com.launchdarkly.observability.api.ObservabilityOptions.TracesApi(
            includeErrors = false,
            includeSpans = false
        )
        val metricsApi = com.launchdarkly.observability.api.ObservabilityOptions.MetricsApi.disabled()
        val instrumentations = com.launchdarkly.observability.api.ObservabilityOptions.Instrumentations(
            crashReporting = false,
            activityLifecycle = false,
            launchTime = false
        )
        val logLevel = com.launchdarkly.observability.api.ObservabilityOptions.LogLevel.INFO
        val logAdapter = LDAndroidLogging.adapter()
        val loggerName = "LaunchDarklyObservabilityPlugin"
        val customHeaders = emptyMap<String, String>()
        val sessionBackgroundTimeoutRaw = 0L
        val debug = false

        val constructors = ObservabilityOptions::class.java.declaredConstructors
            .sortedByDescending { it.parameterTypes.size }

        for (ctor in constructors) {
            val p = ctor.parameterTypes
            val args: Array<Any?>? = when {
                // Newer synthetic: (enabled, ..., loggerName, mask, marker)
                p.size == 18 && p[0] == Boolean::class.javaPrimitiveType && p[16] == Int::class.javaPrimitiveType ->
                    arrayOf(
                        observability.isEnabled,
                        observability.serviceName,
                        observability.serviceVersion,
                        observability.otlpEndpoint,
                        observability.backendUrl,
                        observability.contextFriendlyName,
                        resourceAttributes,
                        customHeaders,
                        sessionBackgroundTimeoutRaw,
                        debug,
                        logLevel,
                        tracesApi,
                        metricsApi,
                        instrumentations,
                        logAdapter,
                        loggerName,
                        0,
                        null
                    )
                // Newer marker-only ctor: (enabled, ..., loggerName, marker)
                p.size == 17 && p[0] == Boolean::class.javaPrimitiveType ->
                    arrayOf(
                        observability.isEnabled,
                        observability.serviceName,
                        observability.serviceVersion,
                        observability.otlpEndpoint,
                        observability.backendUrl,
                        observability.contextFriendlyName,
                        resourceAttributes,
                        customHeaders,
                        sessionBackgroundTimeoutRaw,
                        debug,
                        logLevel,
                        tracesApi,
                        metricsApi,
                        instrumentations,
                        logAdapter,
                        loggerName,
                        null
                    )
                // Older synthetic: (serviceName, ..., loggerName, mask, marker)
                p.size == 17 && p[0] == String::class.java && p[15] == Int::class.javaPrimitiveType ->
                    arrayOf(
                        observability.serviceName,
                        observability.serviceVersion,
                        observability.otlpEndpoint,
                        observability.backendUrl,
                        observability.contextFriendlyName,
                        resourceAttributes,
                        customHeaders,
                        sessionBackgroundTimeoutRaw,
                        debug,
                        logLevel,
                        tracesApi,
                        metricsApi,
                        instrumentations,
                        logAdapter,
                        loggerName,
                        0,
                        null
                    )
                // Older marker-only ctor: (serviceName, ..., loggerName, marker)
                p.size == 16 && p[0] == String::class.java ->
                    arrayOf(
                        observability.serviceName,
                        observability.serviceVersion,
                        observability.otlpEndpoint,
                        observability.backendUrl,
                        observability.contextFriendlyName,
                        resourceAttributes,
                        customHeaders,
                        sessionBackgroundTimeoutRaw,
                        debug,
                        logLevel,
                        tracesApi,
                        metricsApi,
                        instrumentations,
                        logAdapter,
                        loggerName,
                        null
                    )
                else -> null
            }

            if (args != null) {
                try {
                    ctor.isAccessible = true
                    return ctor.newInstance(*args) as ObservabilityOptions
                } catch (_: Throwable) {
                    // Try next constructor shape.
                }
            }
        }

        throw NoSuchMethodError("No compatible ObservabilityOptions constructor found")
    }

    private fun printException(prefix: String, t: Throwable) {
        System.out.println("$prefix ${t::class.java.name}: ${t.message}")
        val writer = java.io.StringWriter()
        val printWriter = java.io.PrintWriter(writer)
        t.printStackTrace(printWriter)
        printWriter.flush()
        System.out.println(writer.toString())
    }

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
        replay: LDSessionReplayOptions
    ) {
        System.out.println("LD:ObservabilityBridge start called 3")

        val resourceAttributes = try { Attributes.builder()
                .put(AttributeKey.stringKey("service.name"), observability.serviceName)
                .put(AttributeKey.stringKey("service.version"), observability.serviceVersion)
                .build()
        } catch (t: Throwable) {
            printException("LD:resourceAttributes failed to build ObservabilityOptions", t)
            throw t
        }

        System.out.println("LD:ObservabilityBridge resourceAttributes called")

        val nativeObservabilityOptions = try {
            createObservabilityOptionsCompat(observability, resourceAttributes)
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

        System.out.println(
            "LD:ObservabilityBridge Session replay enabled=${nativeSessionReplayOptions.enabled}, " +
                "backendUrl=${nativeObservabilityOptions.backendUrl}"
        )

        val ldConfig = try {
            LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
                .mobileKey(mobileKey)
                //.offline(true)
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
            System.out.println("LD:ObservabilityBridge LDClient.init completed")
        } catch (t: Throwable) {
            printException("LD:ObservabilityBridge LDClient.init failed", t)
            throw t
        }
    }

   
}