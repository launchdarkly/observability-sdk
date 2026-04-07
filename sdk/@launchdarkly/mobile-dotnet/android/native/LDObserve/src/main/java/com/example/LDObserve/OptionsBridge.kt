package com.launchdarkly.LDNative

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
