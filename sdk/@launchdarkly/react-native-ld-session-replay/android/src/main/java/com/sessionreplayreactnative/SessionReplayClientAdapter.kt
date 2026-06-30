package com.sessionreplayreactnative

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.ReadableMap
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.context.LDObserveLogging
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
    // Optional `service.version` forwarded from JS. null keeps the SDK default.
    private var serviceVersion: String? = null
    // Optional OTLP endpoint / backend URL forwarded from JS. null keeps the SDK
    // default. backendUrl also drives the session replay upload endpoint (the
    // SessionReplay plugin reads it from the shared observability options).
    private var otlpEndpoint: String? = null
    private var backendUrl: String? = null
    // Optional session id forwarded from the JS observability SDK so the native observability
    // instance (which emits e.g. `click` spans) seeds its session manager with the same
    // `session.id`. null means the native SDK generates and owns its own session.
    private var customSessionId: String? = null
    private var replayOptions: ReplayOptions? = null
    // Only accessed from the main thread (all reads/writes are inside Handler(mainLooper).post blocks).
    private var initialized = false
    // The most recently identified context. Defaults to anonymous; updated on each successful identify.
    private var cachedContext: LDContext =
        LDContext.builder(ContextKind.DEFAULT, "anonymous").anonymous(true).build()
    private val logger = LDLogger.withAdapter(LDAndroidLogging.adapter(), TAG)

    fun setMobileKey(mobileKey: String, options: ReadableMap?) {
        synchronized(lock) {
            this.mobileKey = mobileKey
            this.serviceName = options?.takeIf { it.hasKey("serviceName") }
                ?.getString("serviceName")
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SERVICE_NAME
            this.serviceVersion = options?.takeIf { it.hasKey("serviceVersion") }
                ?.getString("serviceVersion")
                ?.takeIf { it.isNotBlank() }
            this.customSessionId = options?.takeIf { it.hasKey("sessionId") }
                ?.getString("sessionId")
                ?.takeIf { it.isNotBlank() }
            this.otlpEndpoint = options?.takeIf { it.hasKey("otlpEndpoint") }
                ?.getString("otlpEndpoint")
                ?.takeIf { it.isNotBlank() }
            this.backendUrl = options?.takeIf { it.hasKey("backendUrl") }
                ?.getString("backendUrl")
                ?.takeIf { it.isNotBlank() }
            this.replayOptions = replayOptionsFrom(options)
        }
    }

    fun start(application: Application, activity: Activity?, completion: (Boolean, String?) -> Unit) {
        val localMobileKey: String?
        val localServiceName: String
        val localServiceVersion: String?
        val localCustomSessionId: String?
        val localReplayOptions: ReplayOptions?
        val localOtlpEndpoint: String?
        val localBackendUrl: String?

        // Capture configuration under the lock, then release it before posting to the main thread.
        synchronized(lock) {
            localMobileKey = mobileKey
            localReplayOptions = replayOptions
            localServiceName = serviceName
            localServiceVersion = serviceVersion
            localCustomSessionId = customSessionId
            localOtlpEndpoint = otlpEndpoint
            localBackendUrl = backendUrl
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
                    initLDClient(application, localMobileKey, localServiceName, localServiceVersion, localCustomSessionId, localReplayOptions, localOtlpEndpoint, localBackendUrl)
                } catch (e: Exception) {
                    logger.error("start: LDClient.init() threw {0}: {1}", e::class.simpleName, e.message)
                    completion(false, "Session replay failed to initialize.")
                    return@post
                }
                initialized = true
                // React Native is often initialized after the main activity has already been
                // created, so we miss its lifecycle events. Manually register it, just in case.
                activity?.let { LDReplay.registerActivity(it) }
            } else {
                logger.debug("start: already initialized, re-applying isEnabled={0}", localReplayOptions.enabled)
            }
            try {
                applyEnabled(localReplayOptions.enabled)
            } catch (e: Exception) {
                logger.error("start: applyEnabled threw {0}: {1}", e::class.simpleName, e.message)
                completion(false, "Session replay failed to start.")
                return@post
            }
            completion(true, null)
        }
    }

    fun afterIdentify(contextKeys: ReadableMap, canonicalKey: String, completed: Boolean) {
        val keys = mutableMapOf<String, String>()
        val iterator = contextKeys.keySetIterator()
        while (iterator.hasNextKey()) {
            val kind = iterator.nextKey()
            contextKeys.getString(kind)?.let { keys[kind] = it }
        }
        Handler(Looper.getMainLooper()).post {
            try {
                if (completed) {
                    buildContextFromKeys(keys)?.let { cachedContext = it }
                }
                if (initialized) {
                    LDReplay.hookProxy?.afterIdentify(keys, canonicalKey, completed)
                }
            } catch (e: Exception) {
                logger.error("afterIdentify: threw {0}: {1}", e::class.simpleName, e.message)
            }
        }
    }

    fun stop(completion: () -> Unit) {
        logger.debug("stop")
        // Post to the main thread so that stop() queues behind any in-progress start().
        Handler(Looper.getMainLooper()).post {
            try {
                LDReplay.stop()
            } catch (e: Exception) {
                logger.error("stop: threw {0}: {1}", e::class.simpleName, e.message)
            }
            completion()
        }
    }

    private fun initLDClient(
        application: Application,
        mobileKey: String,
        serviceName: String,
        serviceVersion: String?,
        customSessionId: String?,
        replayOptions: ReplayOptions,
        otlpEndpoint: String?,
        backendUrl: String?
    ) {
        logger.debug("initLDClient: calling LDClient.init()")
        // Apply the forwarded service.version only when provided; otherwise keep the SDK default.
        var observabilityOptions = ObservabilityOptions(
            serviceName = serviceName,
            logAdapter = LDObserveLogging.adapter(),
            // Disable the OpenTelemetry Android CrashReporterInstrumentation
            instrumentations = ObservabilityOptions.Instrumentations(
                crashReporting = false,
            ),
        )
        if (serviceVersion != null) {
            observabilityOptions = observabilityOptions.copy(serviceVersion = serviceVersion)
        }
        // Forwarded endpoints (when provided) override the SDK defaults. backendUrl also
        // drives the session replay upload endpoint, since the SessionReplay plugin reads it
        // from the shared observability options.
        if (otlpEndpoint != null) {
            observabilityOptions = observabilityOptions.copy(otlpEndpoint = otlpEndpoint)
        }
        if (backendUrl != null) {
            observabilityOptions = observabilityOptions.copy(backendUrl = backendUrl)
        }
        val config = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(mobileKey)
            .offline(true)
            .plugins(
                Components.plugins().setPlugins(
                    listOf(
                        // Forward the JS observability session id (when provided) so the native
                        // observability instance seeds its session manager with the same
                        // `session.id`, matching iOS. null lets the native SDK own its session.
                        Observability(
                            application = application,
                            mobileKey = mobileKey,
                            options = observabilityOptions,
                            customSessionId = customSessionId
                        ),
                        SessionReplay(options = replayOptions),
                    )
                )
            )
            .build()

        // timeout=0: return immediately without blocking the main thread waiting for flags.
        // onPluginsReady() fires synchronously during init() before it returns.
        LDClient.init(application, config, cachedContext, 0)
    }

    private fun applyEnabled(enabled: Boolean) {
        if (enabled) {
            LDReplay.start()
        } else {
            LDReplay.stop()
        }
    }

    // Analogous to buildObserveContext() in SessionReplayService.kt (observability-android),
    // but builds an LDContext (for LDClient.init()) instead of an LDObserveContext.
    internal fun buildContextFromKeys(contextKeys: Map<String, String>?): LDContext? {
        if (contextKeys.isNullOrEmpty()) return null
        if (contextKeys.size == 1) {
            val (kind, key) = contextKeys.entries.first()
            return LDContext.builder(ContextKind.of(kind), key).build()
        }
        val contexts = contextKeys.map { (kind, key) ->
            LDContext.builder(ContextKind.of(kind), key).build()
        }
        return LDContext.createMulti(*contexts.toTypedArray())
    }

    /**
     * Builds a [ReplayOptions] from the React Native bridge's options map. Returns defaults if
     * the map is null.
     *
     * @param map options dictionary as received from JS, or `null` when no options were provided.
     */
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

        val maskTestIDs = stringListFromMap(map, "maskTestIDs")
        val unmaskTestIDs = stringListFromMap(map, "unmaskTestIDs")

        val frameRate = if (map.hasKey("frameRate")) map.getDouble("frameRate") else 1.0
        val replayScale = if (map.hasKey("scale")) {
            map.getDouble("scale").takeIf { it > 0 }?.toFloat() ?: 1.0f
        } else {
            1.0f
        }
        val minimumAlpha = if (map.hasKey("minimumAlpha")) {
            map.getDouble("minimumAlpha").toFloat()
        } else {
            PrivacyProfile.DEFAULT_MINIMUM_ALPHA
        }
        val sampleRate = if (map.hasKey("sampleRate")) map.getDouble("sampleRate") else 1.0

        return ReplayOptions(
            enabled = isEnabled,
            sampleRate = sampleRate,
            frameRate = frameRate,
            scale = replayScale,
            privacyProfile = PrivacyProfile(
                maskTextInputs = maskTextInputs,
                maskWebViews = maskWebViews,
                maskText = maskText,
                maskImageViews = maskImages,
                maskXMLViewIds = maskTestIDs,
                unmaskXMLViewIds = unmaskTestIDs,
                minimumAlpha = minimumAlpha,
            )
        )
    }

    /**
     * Reads the value at [key] from [map] as a list of strings. Returns an empty list when the
     * key is absent, the array is null, or any element is non-string. Non-string elements are
     * dropped silently.
     *
     * @param map source ReadableMap.
     * @param key key whose value should be a `ReadableArray` of strings.
     */
    private fun stringListFromMap(map: ReadableMap, key: String): List<String> {
        if (!map.hasKey(key)) return emptyList()
        val array = map.getArray(key) ?: return emptyList()
        val out = ArrayList<String>(array.size())
        for (i in 0 until array.size()) {
            array.getString(i)?.let { out.add(it) }
        }
        return out
    }

    companion object {
        val shared = SessionReplayClientAdapter()
        private const val TAG = "LDSessionReplay"
        private const val DEFAULT_SERVICE_NAME = "sessionreplay-react-native"
    }
}
