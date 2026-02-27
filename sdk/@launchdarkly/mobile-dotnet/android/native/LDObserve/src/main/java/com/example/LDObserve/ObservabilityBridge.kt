package com.launchdarkly.LDNative

import android.app.Application
import android.util.Log
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
    @JvmField var serviceName: String = ""
    @JvmField var serviceVersion: String = ""
    @JvmField var otlpEndpoint: String = ""
    @JvmField var backendUrl: String = ""
    @JvmField var contextFriendlyName: String? = null

    constructor()

    constructor(
        serviceName: String,
        serviceVersion: String,
        otlpEndpoint: String,
        backendUrl: String,
        contextFriendlyName: String?
    ) {
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

    public fun getHookProxy(): ObservabilityHookProxy? {
        val real = LDObserve.hookProxy ?: return null
        return ObservabilityHookProxy(real)
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
        val nativeObservabilityOptions = com.launchdarkly.observability.api.ObservabilityOptions(
            resourceAttributes = Attributes.builder()
                .put(AttributeKey.stringKey("service.name"), observability.serviceName)
                .put(AttributeKey.stringKey("service.version"), observability.serviceVersion)
                .build(),
            debug = false,
            otlpEndpoint = observability.otlpEndpoint,
            backendUrl = observability.backendUrl,
            tracesApi = com.launchdarkly.observability.api.ObservabilityOptions.TracesApi(includeErrors = false, includeSpans = false),
            metricsApi = com.launchdarkly.observability.api.ObservabilityOptions.MetricsApi.disabled(),
            instrumentations = com.launchdarkly.observability.api.ObservabilityOptions.Instrumentations(
                crashReporting = false, launchTime = false, activityLifecycle = false
            ),
            logAdapter = LDAndroidLogging.adapter(),
        )

        val observabilityPlugin = Observability(
            application = app,
            mobileKey = mobileKey,
            options = nativeObservabilityOptions
        )

        val sessionReplayPlugin = SessionReplay(
            options = com.launchdarkly.observability.replay.ReplayOptions(
                enabled = replay.isEnabled,
                privacyProfile = com.launchdarkly.observability.replay.PrivacyProfile(
                    maskTextInputs = replay.privacy.maskTextInputs,
                    maskText = replay.privacy.maskLabels,
                    maskImageViews = replay.privacy.maskImages,
                    maskWebViews = replay.privacy.maskWebViews
                )
            )
        )

        Log.i("ObservabilityBridge", "Session replay enabled=${replay.isEnabled}, privacy.maskLabels=${replay.privacy.maskLabels}")

        val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
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

        val context = LDContext.builder(ContextKind.DEFAULT, "maui-user-key")
            .anonymous(true)
            .build()

        LDClient.init(app, ldConfig, context)
    }

   
}