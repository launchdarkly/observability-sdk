package com.sessionreplayreactnative

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.ReadableMap
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.PrivacyProfile
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.observability.sdk.LDReplay
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.LDAndroidLogging
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig

internal class SessionReplayClientAdapter private constructor() {

    private val lock = Any()
    private var mobileKey: String? = null
    private var serviceName: String = DEFAULT_SERVICE_NAME
    private var replayOptions: ReplayOptions? = null
    // Only accessed from the main thread (all reads/writes are inside Handler(mainLooper).post blocks).
    private var initialized = false
    private val logger = LDLogger.withAdapter(LDAndroidLogging.adapter(), TAG)

    fun setMobileKey(mobileKey: String, options: ReadableMap?) {
        synchronized(lock) {
            this.mobileKey = mobileKey
            this.serviceName = options?.takeIf { it.hasKey("serviceName") }
                ?.getString("serviceName")
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SERVICE_NAME
            this.replayOptions = replayOptionsFrom(options)
        }
    }

    fun start(application: Application, completion: (Boolean, String?) -> Unit) {
        val localMobileKey: String?
        val localServiceName: String
        val localReplayOptions: ReplayOptions?

        // Capture configuration under the lock, then release it before posting to the main thread.
        synchronized(lock) {
            localMobileKey = mobileKey
            localReplayOptions = replayOptions
            localServiceName = serviceName
        }
        if (localMobileKey == null || localReplayOptions == null) {
            val msg = "start: configure() was not called — mobile key or options are missing"
            logger.error(msg)
            completion(false, msg)
            return
        }

        // All work runs on the main thread so that:
        //  1. initLDClient() satisfies the main-thread requirement of OpenTelemetryRum.build().
        //  2. Consecutive start()/stop() calls are naturally serialized without locks.
        Handler(Looper.getMainLooper()).post {
            if (!initialized) {
                try {
                    initLDClient(application, localMobileKey, localServiceName, localReplayOptions)
                } catch (e: Exception) {
                    logger.error("start: LDClient.init() threw {0}: {1}", e::class.simpleName, e.message)
                    completion(false, "Session replay failed to initialize.")
                    return@post
                }
                initialized = true
            } else {
                logger.debug("start: already initialized, re-applying isEnabled={0}", localReplayOptions.enabled)
            }
            applyEnabled(localReplayOptions.enabled)
            completion(true, null)
        }
    }

    fun stop(completion: () -> Unit) {
        logger.debug("stop")
        // Post to the main thread so that stop() queues behind any in-progress start().
        Handler(Looper.getMainLooper()).post {
            LDReplay.stop()
            completion()
        }
    }

    private fun initLDClient(application: Application, mobileKey: String, serviceName: String, replayOptions: ReplayOptions) {
        logger.debug("initLDClient: calling LDClient.init()")
        val config = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(mobileKey)
            .offline(true)
            .plugins(
                Components.plugins().setPlugins(
                    listOf(
                        // TODO: Pass JS ObservabilityOptions such as backendUrl,
                        //  resourceAttributes, and sessionBackgroundTimeout through to here.
                        Observability(
                            application = application,
                            mobileKey = mobileKey,
                            options = ObservabilityOptions(
                                serviceName = serviceName,
                                logAdapter = LDAndroidLogging.adapter(),
                            )
                        ),
                        SessionReplay(options = replayOptions),
                    )
                )
            )
            .build()

        // The context key is a placeholder. The LDClient is offline and never sends it to
        // LaunchDarkly servers, but SessionReplay does use it locally to attribute sessions.
        //
        // TODO: Pass the actual initial context here once the LaunchDarkly React Native SDK
        // supports providing a context at initialization time. Currently, context is only
        // available after an explicit client.identify() call — getContext() always returns
        // undefined when register() runs during the LDClient constructor.
        val placeholderContext = LDContext.builder(ContextKind.DEFAULT, "placeholder").build()
        // timeout=0: return immediately without blocking the main thread waiting for flags.
        // onPluginsReady() fires synchronously during init() before it returns.
        LDClient.init(application, config, placeholderContext, 0)
    }

    private fun applyEnabled(enabled: Boolean) {
        if (enabled) {
            LDReplay.start()
        } else {
            LDReplay.stop()
        }
    }

    internal fun replayOptionsFrom(map: ReadableMap?): ReplayOptions {
        if (map == null) {
            return ReplayOptions(
                enabled = true,
                privacyProfile = PrivacyProfile(maskTextInputs = true)
            )
        }

        val isEnabled = if (map.hasKey("isEnabled")) map.getBoolean("isEnabled") else true
        val maskTextInputs = if (map.hasKey("maskTextInputs")) map.getBoolean("maskTextInputs") else true
        val maskWebViews = if (map.hasKey("maskWebViews")) map.getBoolean("maskWebViews") else false
        val maskText = if (map.hasKey("maskLabels")) map.getBoolean("maskLabels") else false
        val maskImages = if (map.hasKey("maskImages")) map.getBoolean("maskImages") else false

        return ReplayOptions(
            enabled = isEnabled,
            privacyProfile = PrivacyProfile(
                maskTextInputs = maskTextInputs,
                maskWebViews = maskWebViews,
                maskText = maskText,
                maskImageViews = maskImages,
            )
        )
    }

    companion object {
        val shared = SessionReplayClientAdapter()
        private const val TAG = "LDSessionReplay"
        private const val DEFAULT_SERVICE_NAME = "sessionreplay-react-native"
    }
}
