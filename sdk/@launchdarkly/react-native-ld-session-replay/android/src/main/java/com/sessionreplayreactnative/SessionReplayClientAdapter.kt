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
import kotlin.time.Duration.Companion.minutes

internal class SessionReplayClientAdapter private constructor() {

    private enum class State { IDLE, STARTING, STARTED }

    private val lock = Any()
    private var mobileKey: String? = null
    private var serviceName: String = DEFAULT_SERVICE_NAME
    private var replayOptions: ReplayOptions? = null
    private var state = State.IDLE
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
        val key: String
        val svcName: String
        val options: ReplayOptions
        val currentState: State

        synchronized(lock) {
            key = mobileKey ?: run {
                val msg = "start: configure() was not called — mobile key is missing"
                logger.error(msg)
                completion(false, msg)
                return
            }
            // replayOptions is always set alongside mobileKey in setMobileKey(), so it is
            // non-null whenever mobileKey is non-null (checked above).
            options = replayOptions!!
            svcName = serviceName
            currentState = state
            if (state == State.IDLE) state = State.STARTING
        }

        when (currentState) {
            State.IDLE -> {
                // initLDClient() must run on the main thread because OpenTelemetryRum.build()
                // calls lifecycle.addObserver() internally, which requires the main thread.
                // Using timeout=0 so we don't block the UI — onPluginsReady() fires
                // synchronously during LDClient.init() regardless of the flag-fetch timeout.
                Handler(Looper.getMainLooper()).post {
                    try {
                        initLDClient(application, key, svcName, options)
                    } catch (e: Exception) {
                        logger.error("start: LDClient.init() threw {0}: {1}", e::class.simpleName, e.message)
                        synchronized(lock) { state = State.IDLE }
                        completion(false, "Session replay failed to initialize.")
                        return@post
                    }
                    // onPluginsReady() fires synchronously during LDClient.init(), so
                    // LDObserve.init() and sessionReplayService.initialize() have both been
                    // called by the time initLDClient() returns.
                    applyEnabled(options.enabled)
                    synchronized(lock) { state = State.STARTED }
                    completion(true, null)
                }
            }
            State.STARTING -> {
                // A stop() called here will invoke LDReplay.stop() before LDClient.init()
                // has completed on the main thread, making it a no-op. When init finishes,
                // applyEnabled() will start replay regardless. This is an accepted race
                // shared with the iOS implementation; fixing it would require queuing
                // pending completions.
                logger.warn("start: unexpected concurrent call while already starting")
                completion(true, null)
            }
            State.STARTED -> {
                logger.debug("start: already started, re-applying isEnabled={0}", options.enabled)
                applyEnabled(options.enabled)
                completion(true, null)
            }
        }
    }

    fun stop(completion: () -> Unit) {
        logger.debug("stop")
        LDReplay.stop()
        completion()
    }

    private fun initLDClient(application: Application, mobileKey: String, serviceName: String, replayOptions: ReplayOptions) {
        logger.debug("initLDClient: calling LDClient.init()")
        val config = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(mobileKey)
            .offline(true)
            .plugins(
                Components.plugins().setPlugins(
                    listOf(
                        Observability(
                            application = application,
                            mobileKey = mobileKey,
                            options = ObservabilityOptions(
                                serviceName = serviceName,
                                sessionBackgroundTimeout = 10.minutes,
                                logAdapter = LDAndroidLogging.adapter(),
                            )
                        ),
                        SessionReplay(options = replayOptions),
                    )
                )
            )
            .build()

        // The context key is a placeholder — the LDClient is offline and never sends it to
        // LaunchDarkly servers. The React Native LDClient manages the real user context.
        val context = LDContext.builder(ContextKind.DEFAULT, "12345").build()
        // timeout=0: return immediately without blocking the main thread waiting for flags.
        // onPluginsReady() fires synchronously during init() before it returns.
        LDClient.init(application, config, context, 0)
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
