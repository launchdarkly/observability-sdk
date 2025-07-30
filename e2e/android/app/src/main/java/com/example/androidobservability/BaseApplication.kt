package com.example.androidobservability

import android.app.Application
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.sdk.android.integrations.Plugin
import java.util.Collections

class BaseApplication : Application() {

    companion object {
        // Set LAUNCHDARKLY_MOBILE_KEY to your LaunchDarkly SDK mobile key.
        const val LAUNCHDARKLY_MOBILE_KEY = "mob-a9c9ebd5-5b37-4b95-a418-c5ee4cd89468"
    }

    override fun onCreate() {
        super.onCreate()

        // Set LAUNCHDARKLY_MOBILE_KEY to your LaunchDarkly mobile key found on the LaunchDarkly
        // dashboard in the start guide.
        // If you want to disable the Auto EnvironmentAttributes functionality.
        // Use AutoEnvAttributes.Disabled as the argument to the Builder
        val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(LAUNCHDARKLY_MOBILE_KEY)
            .plugins(Components.plugins().setPlugins(
                Collections.singletonList<Plugin>(Observability(this@BaseApplication))
            ))
            .build()

        // Set up the context properties. This context should appear on your LaunchDarkly context
        // dashboard soon after you run the demo.
        val context = LDContext.builder(ContextKind.DEFAULT, "example-user-key")
            .anonymous(true)
            .build()

        LDClient.init(this@BaseApplication, ldConfig, context)
    }
}