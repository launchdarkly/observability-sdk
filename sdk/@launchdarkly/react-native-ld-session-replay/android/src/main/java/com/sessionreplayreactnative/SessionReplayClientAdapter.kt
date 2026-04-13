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

    private val lock = Any()
    private var mobileKey: String? = null
    private var serviceName: String = DEFAULT_SERVICE_NAME
    private var replayOptions: ReplayOptions? = null
    // Only accessed from the main thread (all reads/writes are inside Handler(mainLooper).post blocks).
    private var initialized = false
    private var ldClient: LDClient? = null
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

        // Capture configuration under the lock, then release it before posting to the main thread.
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
        }

        // All work runs on the main thread so that:
        //  1. initLDClient() satisfies the main-thread requirement of OpenTelemetryRum.build().
        //  2. Consecutive start()/stop()/identify() calls are naturally serialized without locks
        //     — the main thread queue acts as an implicit mutex.
        Handler(Looper.getMainLooper()).post {
            if (!initialized) {
                try {
                    initLDClient(application, key, svcName, options)
                } catch (e: Exception) {
                    logger.error("start: LDClient.init() threw {0}: {1}", e::class.simpleName, e.message)
                    completion(false, "Session replay failed to initialize.")
                    return@post
                }
                initialized = true
            } else {
                logger.debug("start: already initialized, re-applying isEnabled={0}", options.enabled)
            }
            applyEnabled(options.enabled)
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

    fun identify(map: ReadableMap, completion: () -> Unit) {
        val context = ldContextFrom(map)
        // Post to the main thread so that identify() is serialized with start()/stop() and
        // ldClient is guaranteed to be set (start() always runs before identify() on the
        // main thread queue).
        Handler(Looper.getMainLooper()).post {
            ldClient?.identify(context)
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

        // The context key is a placeholder. The LDClient is offline and never sends it to
        // LaunchDarkly servers, but SessionReplay does use it locally to attribute sessions
        // until the real context is provided via identify(). The real user context is relayed
        // from the TypeScript afterIdentify hook, which fires whenever the React Native
        // LDClient identifies.
        //
        // TODO: Pass the actual initial context here once the LaunchDarkly React Native SDK
        // supports providing a context at initialization time. Currently, context is only
        // available after an explicit client.identify() call — getContext() always returns
        // undefined when register() runs during the LDClient constructor.
        val placeholderContext = LDContext.builder(ContextKind.DEFAULT, "placeholder").build()
        // timeout=0: return immediately without blocking the main thread waiting for flags.
        // onPluginsReady() fires synchronously during init() before it returns.
        ldClient = LDClient.init(application, config, placeholderContext, 0)
    }

    private fun applyEnabled(enabled: Boolean) {
        if (enabled) {
            LDReplay.start()
        } else {
            LDReplay.stop()
        }
    }

    // SessionReplay only needs kind and key to identify sessions — see SessionReplayHook.afterIdentify()
    // in the observability-android SDK, which extracts only context.kind and context.key into a
    // Map<String, String>. Name, email, and custom attributes are not forwarded.
    internal fun ldContextFrom(map: ReadableMap): LDContext {
        val kind = if (map.hasKey("kind")) map.getString("kind") else null
        if (kind == "multi") {
            val builder = LDContext.multiBuilder()
            map.toHashMap().forEach { (k, v) ->
                if (k == "kind") return@forEach
                @Suppress("UNCHECKED_CAST")
                val key = (v as? HashMap<String, Any>)?.get("key") as? String ?: return@forEach
                builder.add(LDContext.create(ContextKind.of(k), key))
            }
            return builder.build()
        }
        val key = if (map.hasKey("key")) map.getString("key") ?: "unknown" else "unknown"
        val ctxKind = if (kind != null && kind != "user") ContextKind.of(kind) else ContextKind.DEFAULT
        return LDContext.builder(ctxKind, key).build()
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
