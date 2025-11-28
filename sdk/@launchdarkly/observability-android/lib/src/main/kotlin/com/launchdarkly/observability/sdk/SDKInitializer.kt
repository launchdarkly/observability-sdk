package com.launchdarkly.observability.sdk

import android.app.Application
import com.launchdarkly.observability.api.Options
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.PrivacyProfile
import com.launchdarkly.observability.replay.ReplayInstrumentation
import com.launchdarkly.observability.replay.ReplayOptions
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

object SDKInitializer {

    fun init(
        application: Application,
        sdkKey: String
    ) {
        val pluginOptions = Options(
            resourceAttributes = Attributes.of(
                AttributeKey.stringKey("example"), "value"
            ),
            debug = true,
            logAdapter = LDAndroidLogging.adapter(),
            // TODO: consider these being factories so that the obs plugin can pass instantiation data, log adapter
            instrumentations = listOf(
                ReplayInstrumentation(
                    options = ReplayOptions(
                        privacyProfile = PrivacyProfile(maskText = false)
                    )
                )
            ),
        )

        val observabilityPlugin = Observability(
            application = application,
            mobileKey = sdkKey,
            options = pluginOptions
        )

        // Set LAUNCHDARKLY_MOBILE_KEY to your LaunchDarkly mobile key found on the LaunchDarkly
        // dashboard in the start guide.
        // If you want to disable the Auto EnvironmentAttributes functionality.
        // Use AutoEnvAttributes.Disabled as the argument to the Builder
        val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(sdkKey)
            .plugins(
                Components.plugins().setPlugins(
                    Collections.singletonList<Plugin>(observabilityPlugin)
                )
            )
            .build()

        // Set up the context properties. This context should appear on your LaunchDarkly context
        // dashboard soon after you run the demo.
        val context = LDContext.builder(ContextKind.DEFAULT, "example-user-key")
            .anonymous(true)
            .build()

        LDClient.init(application, ldConfig, context)
    }
}
