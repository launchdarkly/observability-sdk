package com.launchdarkly.LDNative

import android.app.Application
import com.example.LDObserve.BridgeLogger
import com.example.LDObserve.SystemOutBridgeLogger
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.bridge.AttributeConverter
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.LDAndroidLogging
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig

public class ObservabilityBridge(
    private val logger: BridgeLogger = SystemOutBridgeLogger()
) {
    var isDebug: Boolean = true

    public fun getSessionReplayHookProxy(): RealSessionReplayHookProxy? {
        val real = LDReplay.hookProxy ?: return null
        return RealSessionReplayHookProxy(real)
    }

    public fun version(): String {
        return BuildConfig.OBSERVABILITY_SDK_VERSION
    }

    public fun recordError(message: String, cause: String?) {
        LDObserve.recordError(message, cause)
    }

    public fun recordMetric(name: String, value: Double) {
        LDObserve.recordMetric(Metric(name = name, value = value))
    }

    public fun recordCount(name: String, value: Double) {
        LDObserve.recordCount(Metric(name = name, value = value))
    }

    public fun recordIncr(name: String, value: Double) {
        LDObserve.recordIncr(Metric(name = name, value = value))
    }

    public fun recordHistogram(name: String, value: Double) {
        LDObserve.recordHistogram(Metric(name = name, value = value))
    }

    public fun recordUpDownCounter(name: String, value: Double) {
        LDObserve.recordUpDownCounter(Metric(name = name, value = value))
    }

    public fun start(
        app: Application,
        mobileKey: String,
        observability: LDObservabilityOptions,
        replay: LDSessionReplayOptions,
        observabilityVersion: String
    ) {
        logger.info("LD:ObservabilityBridge start called, ver" + observabilityVersion)

        val resourceAttributes = try {
            AttributeConverter.convert(observability.attributes)
        } catch (t: Throwable) {
            printException("LD:resourceAttributes failed to build resourceAttributes", t)
            throw t
        }

        val nativeObservabilityOptions = try {
            com.launchdarkly.observability.api.ObservabilityOptions(
                enabled = observability.isEnabled,
                serviceName = observability.serviceName,
                serviceVersion = observability.serviceVersion,
                resourceAttributes = resourceAttributes,
                debug = false,
                otlpEndpoint = observability.otlpEndpoint,
                backendUrl = observability.backendUrl,
                tracesApi = com.launchdarkly.observability.api.ObservabilityOptions.TracesApi(includeErrors = true, includeSpans = true),
                metricsApi = com.launchdarkly.observability.api.ObservabilityOptions.MetricsApi.enabled(),
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

        observabilityPlugin.distroAttributes = mapOf(
            "telemetry.distro.name" to "observability-maui-android",
            "telemetry.distro.version" to observabilityVersion
        )

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

