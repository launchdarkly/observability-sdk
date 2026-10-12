package com.launchdarkly.LDNative

public class LDObservabilityOptions {
    @JvmField var isEnabled: Boolean = true
    @JvmField var serviceName: String = ""
    @JvmField var serviceVersion: String = ""
    @JvmField var otlpEndpoint: String = ""
    @JvmField var backendUrl: String = ""
    @JvmField var contextFriendlyName: String? = null
    @JvmField var attributes: HashMap<String, Any?>? = null
    @JvmField var launchTime: Boolean = true
    @JvmField var customHeaders: HashMap<String, String>? = null
    // OpenTelemetry severity number; Int.MAX_VALUE disables log export.
    @JvmField var logsApiLevel: Int = 9

    constructor()

    constructor(
        isEnabled: Boolean,
        serviceName: String,
        serviceVersion: String,
        otlpEndpoint: String,
        backendUrl: String,
        contextFriendlyName: String?,
        attributes: HashMap<String, Any?>?,
        launchTime: Boolean,
        customHeaders: HashMap<String, String>?,
        logsApiLevel: Int
    ) {
        this.isEnabled = isEnabled
        this.serviceName = serviceName
        this.serviceVersion = serviceVersion
        this.otlpEndpoint = otlpEndpoint
        this.backendUrl = backendUrl
        this.contextFriendlyName = contextFriendlyName
        this.attributes = attributes
        this.launchTime = launchTime
        this.customHeaders = customHeaders
        this.logsApiLevel = logsApiLevel
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
    @JvmField var sampleRate: Double = 1.0
    @JvmField var frameRate: Double = 1.0
    @JvmField var scale: Double = 1.0
    @JvmField var imageQuality: Double = 0.3

    constructor()

    constructor(
        isEnabled: Boolean,
        serviceName: String,
        privacy: LDPrivacyOptions,
        sampleRate: Double,
        frameRate: Double,
        scale: Double,
        imageQuality: Double
    ) {
        this.isEnabled = isEnabled
        this.serviceName = serviceName
        this.privacy = privacy
        this.sampleRate = sampleRate
        this.frameRate = frameRate
        this.scale = scale
        this.imageQuality = imageQuality
    }
}
