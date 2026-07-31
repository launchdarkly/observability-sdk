package com.launchdarkly.observability.replay

import android.app.Activity
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.client.AppLaunchTracker
import com.launchdarkly.observability.client.AppLifecycleSignal
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.replay.capture.CaptureManager
import com.launchdarkly.observability.replay.capture.ImageCaptureService
import com.launchdarkly.observability.replay.capture.ImageCaptureServicing
import com.launchdarkly.observability.replay.exporter.AppLifecycleItemPayload
import com.launchdarkly.observability.replay.exporter.AppLaunchItemPayload
import com.launchdarkly.observability.replay.exporter.IdentifyItemPayload
import com.launchdarkly.observability.replay.exporter.ImageItemPayload
import com.launchdarkly.observability.replay.exporter.InteractionItemPayload
import com.launchdarkly.observability.replay.exporter.NavigateItemPayload
import com.launchdarkly.observability.replay.exporter.SessionReplayExporter
import com.launchdarkly.observability.replay.exporter.TrackItemPayload
import com.launchdarkly.observability.replay.transport.BatchWorker
import com.launchdarkly.observability.replay.transport.EventQueue
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.sdk.SessionReplayServicing
import com.launchdarkly.observability.util.requireMainThread
import io.opentelemetry.android.session.SessionManager
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Provides session replay instrumentation. Session replays that are sampled will appear on the LaunchDarkly dashboard.
 *
 * @param options Configuration options for replay behavior including privacy settings and capture interval
 * @param observabilityContext Shared context provided by the Observability plugin
 * @param imageCaptureService Optional capture implementation. Defaults to [ImageCaptureService].
 *
 * @sample
 * ```kotlin
 * val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
 *     .mobileKey("your-mobile-key")
 *     .plugins(
 *         Components.plugins().setPlugins(
 *             listOf(
 *                 Observability(this@MyApplication, "your-mobile-key"),
 *                 SessionReplay(
 *                     ReplayOptions(
 *                         privacyProfile = PrivacyProfile.STRICT,
 *                     )
 *                 )
 *             )
 *         )
 *     )
 *     .build()
 * ```
 *
 * @see ReplayOptions for configuration options
 * @see PrivacyProfile for privacy settings
 */
class SessionReplayService(
    private val options: ReplayOptions = ReplayOptions(),
    private val observabilityContext: ObservabilityContext,
    private val imageCaptureService: ImageCaptureServicing? = null,
) : SessionReplayServicing {

    private lateinit var sessionManager: SessionManager
    private val logger: ObserveLogger = observabilityContext.logger
    private val eventQueue = EventQueue()
    private val batchWorker = BatchWorker(eventQueue, logger)
    private var captureManager: CaptureManager? = null
    private var interactionSource: InteractionSource? = null
    private val instrumentationScope = CoroutineScope(DispatcherProviderHolder.current.default + SupervisorJob())
    private var captureJob: Job? = null
    private val shouldCapture = MutableStateFlow(false)
    /**
     * Whether the backend has cleared this launch for screenshots. Starts withheld when a previous launch
     * was refused, and is released once `initializeSession` succeeds.
     */
    private val isScreenCaptureAllowed = MutableStateFlow(true)
    private val _isEnabled = MutableStateFlow(options.enabled)
    private val _isRunning = MutableStateFlow(false)
    private val sampleRate = options.sampleRate
    private val samplingSession = SessionReplaySamplingSession()
    private var processLifecycleObserver: DefaultLifecycleObserver? = null
    private var isInstalled: Boolean = false
    private var exporter: SessionReplayExporter? = null
    private val pendingIdentifyLock = Any()
    private var pendingIdentify: IdentifyItemPayload? = null
    private var initializationStore: SessionReplayInitializationStore? = null
    /**
     * Decides whether screenshots may be taken, from this launch's verdicts and the failure cached from a
     * previous launch. Verdicts arrive on the export thread, so every access goes through [withGate].
     */
    private val recordingGateLock = Any()
    private var recordingGate = SessionReplayRecordingGate(hasCachedFailure = false)

    /**
     * Installs replay instrumentation. Idempotent.
     *
     * Returns `true` if the service is now installed (by this call or a previous one).
     * Callers must consult the return value before publishing this service as the live replay
     * backend (e.g. via [com.launchdarkly.observability.sdk.LDReplay.init]); binding an
     * uninstalled service permanently routes pre-init buffered calls into a non-functional
     * instance.
     */
    fun initialize(): Boolean {
        requireMainThread { "SessionReplayService must be initialized on the main thread" }

        if (isInstalled) return true

        val sm = observabilityContext.sessionManager ?: run {
            logger.error("SessionReplayService.initialize() called before sessionManager is available; skipping.")
            return false
        }
        sessionManager = sm

        captureManager = CaptureManager(
            sessionManager = sm,
            options = options,
            logger = observabilityContext.logger,
            imageCaptureService = imageCaptureService
                ?: ImageCaptureService(options, logger),
        )
        val density = observabilityContext.application.resources.displayMetrics.density
        interactionSource = InteractionSource(sm, options.scale, density)

        val initialIdentifyItemPayload = IdentifyItemPayload.from(
            contextFriendlyName = observabilityContext.options.contextFriendlyName,
            resourceAttributes = observabilityContext.resourceAttributes,
            sessionId = null
        )
        val application = observabilityContext.application
        val appName = try {
            application.packageManager.getApplicationLabel(application.applicationInfo).toString()
        } catch (_: Exception) {
            "Android app"
        }
        // The launch signal is resolved during Observability init (before this runs), so build the
        // `Launch` breadcrumb once here on the main thread and inject it — the exporter never reads
        // the shared context on its background export thread.
        val initialAppLaunchItemPayload = observabilityContext.appLaunchSignal?.let { signal ->
            AppLaunchItemPayload(
                launchType = signal.launchType.wireValue,
                version = signal.version,
                build = signal.build,
                previousVersion = signal.previousVersion,
                timestamp = signal.timestamp,
                sessionId = null
            )
        }
        val store = SessionReplayInitializationStore(
            prefs = application.getSharedPreferences(AppLaunchTracker.PREFS_NAME, Context.MODE_PRIVATE),
            sdkKey = observabilityContext.sdkKey,
        )
        val cachedFailure = store.loadFailure()
        initializationStore = store
        recordingGate = SessionReplayRecordingGate(hasCachedFailure = cachedFailure != null)
        isScreenCaptureAllowed.value = recordingGate.isScreenCaptureAllowed
        if (cachedFailure != null) {
            logger.info(
                "Session replay is holding screenshots until initializeSession succeeds, " +
                    "previous launch failed with: ${cachedFailure.reason}"
            )
        }

        val exporter = SessionReplayExporter(
            organizationVerboseId = observabilityContext.sdkKey,
            backendUrl = observabilityContext.options.backendUrl,
            serviceName = observabilityContext.options.serviceName,
            serviceVersion = observabilityContext.options.serviceVersion,
            initialIdentifyItemPayload = initialIdentifyItemPayload,
            title = appName,
            logger = logger,
            initialAppLaunchItemPayload = initialAppLaunchItemPayload,
            onInitializationVerdict = ::handleInitializationVerdict,
        )
        this@SessionReplayService.exporter = exporter
        batchWorker.addExporter(exporter)
        batchWorker.start()

        startCollectors()
        startCaptureStateObserver()
        startProcessLifecycleObserver()

        if (_isEnabled.value) {
            attemptStart(ignoreSampling = false)
        }

        isInstalled = true
        return true
    }

    private fun startCollectors() {
        // Images collector
        instrumentationScope.launch {
            captureManager?.captureFlow?.collect { capture ->
                if (!_isRunning.value) return@collect
                eventQueue.send(ImageItemPayload(capture))
            }
        }

        // Feed raw touches from the shared Observability hook into the (scaling + grouping) source.
        instrumentationScope.launch {
            observabilityContext.userInteractionManager?.touchFlow?.collect { sample ->
                interactionSource?.process(sample)
            }
        }

        // Interactions collector
        instrumentationScope.launch {
            interactionSource?.captureFlow?.collect { interaction ->
                if (!_isRunning.value) return@collect
                eventQueue.send(InteractionItemPayload(interaction))
            }
        }

        // Navigate collector: each screen change from Observability becomes an rrweb `Navigate`
        // event on the active recording.
        instrumentationScope.launch {
            observabilityContext.screenViewFlow?.collect { screenView ->
                if (!_isRunning.value) return@collect
                eventQueue.send(
                    NavigateItemPayload(
                        name = screenView.name,
                        timestamp = screenView.timestamp,
                        sessionId = sessionManager.getSessionId()
                    )
                )
            }
        }

        // Track collector: each track event from Observability's single emitter becomes an rrweb
        // `Track` event. This covers both `LDClient.track` and the manual `LDObserve.track` API
        // (including standalone init without `LDClient`), which the LD client hook alone misses.
        instrumentationScope.launch {
            observabilityContext.trackFlow?.collect { track ->
                recordTrack(track.name, track.metricValue, track.attributes, track.timestamp)
            }
        }

        // App-lifecycle collector: each foreground/background transition from Observability becomes
        // an rrweb `Foreground` / `Background` breadcrumb on the active recording.
        instrumentationScope.launch {
            observabilityContext.appLifecycleFlow?.collect { signal ->
                if (!_isRunning.value) return@collect
                val tag = when (signal.kind) {
                    AppLifecycleSignal.Kind.FOREGROUND -> RRWebCustomDataTag.APP_FOREGROUND
                    AppLifecycleSignal.Kind.BACKGROUND -> RRWebCustomDataTag.APP_BACKGROUND
                }
                eventQueue.send(
                    AppLifecycleItemPayload(
                        tag = tag,
                        lifecycleState = signal.lifecycleState,
                        timestamp = signal.timestamp,
                        sessionId = sessionManager.getSessionId()
                    )
                )
                // The Background marker must land in the export that fires when the app backgrounds.
                // The process-lifecycle onStop flush runs from a separate observer and can race
                // ahead of this asynchronous enqueue, dropping the marker from that batch. The
                // enqueue above is synchronous, so flushing here guarantees a flush batch that
                // includes the just-queued Background breadcrumb.
                if (signal.kind == AppLifecycleSignal.Kind.BACKGROUND) {
                    batchWorker.flush()
                }
            }
        }

        // The `Launch` breadcrumb is not collected here: Observability resolves the launch signal
        // synchronously at start (before this service registers), so it is read once in
        // `initialize()` and injected into the exporter, which emits it on the first wake-up batch.
    }

    /**
     * Observes [shouldCapture] state changes and synchronizes the capture loop state.
     * Skips redundant operations if the desired state already matches the current state.
     */
    private fun startCaptureStateObserver() {
        instrumentationScope.launch {
            var hasEnabledTouchCapture = false

            combine(shouldCapture, _isRunning, isScreenCaptureAllowed) { shouldRun, running, screenshotsAllowed ->
                (shouldRun && running) to screenshotsAllowed
            }
                .collect { (isRecording, screenshotsAllowed) ->
                    // Session Replay needs the shared touch hook regardless of
                    // `instrumentations.userTaps`, and regardless of [isScreenCaptureAllowed]: while
                    // screenshots are withheld, interactions still queue and are pushed once the backend
                    // accepts the session. Both calls are idempotent: attach ensures the current window is
                    // tracked (Observability already attaches at init), and enable wraps that
                    // already-current window plus future ones - so capture starting after the first
                    // activity is up still records its touches.
                    if (isRecording && !hasEnabledTouchCapture) {
                        hasEnabledTouchCapture = true
                        observabilityContext.userInteractionManager?.apply {
                            attachToApplication(observabilityContext.application)
                            enableTouchCapture()
                        }
                    }

                    val shouldCaptureScreen = isRecording && screenshotsAllowed
                    if (shouldCaptureScreen == (captureJob?.isActive == true)) return@collect
                    if (shouldCaptureScreen) doRunCapture() else doPauseCapture()
                }
        }
    }

    private suspend fun doRunCapture() {
        captureJob?.cancelAndJoin()
        captureJob = instrumentationScope.launch {
            logger.debug("Session replay capture running")
            while (isActive) {
                // Backpressure: when the event queue is saturated a captured frame
                // would just be dropped at send time, so skip the expensive capture
                // work entirely and re-check after the normal cadence. Mirrors iOS's
                // `isEventQueueAvailable` gate on `queueSnapshot`.
                if (eventQueue.isFull()) {
                    delay(captureManager?.captureDelayMillis ?: Long.MAX_VALUE)
                    continue
                }
                try {
                    captureManager?.captureNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    logger.error("Capture paused due to OOM", e)
                    shouldCapture.value = false
                    return@launch
                } catch (e: Exception) {
                    logger.error("Capture failed", e)
                }
                delay(captureManager?.captureDelayMillis ?: Long.MAX_VALUE)
            }
        }
    }

    private suspend fun doPauseCapture() {
        captureJob?.cancelAndJoin()
        captureJob = null
        logger.debug("Session replay capture paused")
    }

    private fun runCapture() {
        shouldCapture.value = true
    }

    private fun pauseCapture() {
        shouldCapture.value = false
    }

    /**
     * Whether replay capture is enabled. Setting to `true` evaluates [ReplayOptions.sampleRate]
     * once per enable cycle and starts recording when selected; setting to `false` pauses event
     * production and resets the sampling decision (mirroring `stop()` on iOS).
     */
    override var isEnabled: Boolean
        get() = _isEnabled.value
        set(value) {
            if (_isEnabled.value == value) return
            if (value) {
                // A launch the backend has refused cannot record again, so enabling must not report itself
                // as enabled. The next launch tries again. Checking and enabling under the gate's lock, the
                // same one a verdict is applied under, keeps a refusal that lands in between from being
                // overwritten by the enable that raced it.
                val canStart = withGate { canStartRecording.also { if (it) _isEnabled.value = true } }
                if (!canStart) {
                    logger.info("Session replay cannot start, the backend refused this launch.")
                    return
                }
                attemptStart(ignoreSampling = false)
            } else {
                _isEnabled.value = false
                stopRecording()
            }
        }

    /**
     * Whether this session was selected by [ReplayOptions.sampleRate] and is actively recording.
     * When [isEnabled] is true but sampling excluded this session, this stays false.
     */
    internal val isRunning: Boolean
        get() = _isRunning.value

    private fun attemptStart(ignoreSampling: Boolean): Boolean {
        if (_isRunning.value) return true
        // The exporter stops talking to the backend after an unrecoverable refusal, so starting would only
        // collect events that can never be pushed.
        if (!withGate { canStartRecording }) {
            logger.info("Session replay cannot start, the backend refused this launch.")
            return false
        }
        if (!samplingSession.shouldStartCapture(ignoreSampling, sampleRate)) {
            logger.info("Session replay skipped by sampling.")
            return false
        }
        // Re-checked while holding the gate's lock, the one a verdict is applied under, because a refusal
        // can land while sampling is being evaluated above: without this, its `stopRecording` would be
        // undone here and the collectors would keep running for a launch reporting itself disabled.
        val canStart = withGate { canStartRecording.also { if (it) _isRunning.value = true } }
        if (!canStart) {
            logger.info("Session replay cannot start, the backend refused this launch.")
            return false
        }
        flushPendingIdentify()
        // Ask the backend up front instead of waiting for the first export batch, so a refusal can stop
        // (or keep withholding) screenshots at the very start of the launch. Input events keep flowing into
        // the event queue meanwhile: they buffer there until the queue overflows, and are pushed as soon as
        // the session is accepted.
        startInitializationProbe()
        return true
    }

    private fun stopRecording() {
        samplingSession.reset()
        _isRunning.value = false
    }

    /**
     * Initializes the current session on the backend, off the export path.
     */
    private fun startInitializationProbe() {
        if (!this::sessionManager.isInitialized) return
        val exporterSnapshot = exporter ?: return

        val sessionId = sessionManager.getSessionId()
        instrumentationScope.launch { exporterSnapshot.prepareSession(sessionId) }
    }

    /**
     * Asks the backend again while screenshots are withheld by a failure cached from a previous launch. A
     * launch that starts in the background often probes with no network, or is suspended mid-request, and
     * becoming visible is the moment worth retrying: with capture withheld there are no frames to export,
     * so nothing else would trigger an attempt until the user happens to tap or navigate.
     */
    private fun retryInitializationIfWithheld() {
        if (!_isRunning.value) return
        if (!withGate { isAwaitingVerdict }) return

        startInitializationProbe()
    }

    /**
     * Applies a recording verdict from the exporter: an accepted session releases screen capture (and
     * clears any cached failure), a refused one ends recording for this launch and is remembered so the
     * next launch does not take screenshots before the backend has answered.
     */
    private fun handleInitializationVerdict(verdict: SessionReplayInitializationVerdict) {
        // Recording is over for this launch, so report replay as disabled rather than leaving `isEnabled`
        // claiming otherwise. Set under the gate's lock, together with the verdict that decided it, so an
        // `isEnabled = true` racing this refusal cannot end up as the last write.
        val outcome = withGate {
            apply(verdict).also {
                if (it is SessionReplayRecordingGate.Outcome.StopRecording) _isEnabled.value = false
            }
        }

        when (outcome) {
            is SessionReplayRecordingGate.Outcome.None -> Unit

            is SessionReplayRecordingGate.Outcome.ReleaseScreenCapture -> {
                initializationStore?.clearFailure()
                logger.info("Session replay recovered, resuming screenshots.")
                isScreenCaptureAllowed.value = true
            }

            is SessionReplayRecordingGate.Outcome.StopRecording -> {
                initializationStore?.store(outcome.reason)
                logger.error("Session replay stopped, the backend refused this launch: ${outcome.reason}")
                isScreenCaptureAllowed.value = false
                stopRecording()
            }
        }
    }

    private inline fun <T> withGate(block: SessionReplayRecordingGate.() -> T): T =
        synchronized(recordingGateLock) { recordingGate.block() }

    override fun flush() {
        batchWorker.flush()
    }

    private fun startProcessLifecycleObserver() {
        if (processLifecycleObserver != null) return

        val observer = object : DefaultLifecycleObserver {
            // Pause/resume on the process *resumed* (active) state rather than the
            // *started* (visible) state, so capture stops as soon as the app goes
            // inactive — a transient interruption such as the notification shade,
            // the app switcher, or an incoming call — matching iOS's
            // willResignActive/didBecomeActive gating. ProcessLifecycleOwner
            // debounces ON_PAUSE/ON_RESUME at the process level, so brief in-app
            // activity transitions and configuration changes don't flicker capture.
            override fun onResume(owner: LifecycleOwner) {
                runCapture()
                retryInitializationIfWithheld()
            }

            override fun onPause(owner: LifecycleOwner) {
                pauseCapture()
            }

            // Flush buffered events once fully backgrounded (no longer visible).
            override fun onStop(owner: LifecycleOwner) {
                batchWorker.flush()
            }
        }

        processLifecycleObserver = observer
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)

        // Ensure we don't miss the initial active state when installing late.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCapture()
        }
    }

    /**
     * Registers [activity] for touch capture. Call this after SDK initialization when the
     * activity is already running (e.g. React Native, where init happens after the activity starts).
     */
    override fun registerActivity(activity: Activity) {
        // Touch capture is owned by Observability's shared hook.
        observabilityContext.userInteractionManager?.registerActivity(activity)
        // Screen capture is also owned by Observability. The automatic source only fires on future
        // onActivityResumed callbacks, so capture the already-visible screen here to avoid missing
        // the first screen_view span and Navigate event on late init (e.g. React Native).
        observabilityContext.screenViewManager?.registerActivity(activity)
    }

    // TODO: O11Y-621 - This should be called somewhere (Probably inside ObservabilityService.kt) to shutdown the instrumentation.
    fun shutdown() {
        pauseCapture()
        stopProcessLifecycleObserver()
        batchWorker.stop()
        instrumentationScope.cancel()
        // The touch hook is owned by Observability; do not detach it here.
    }

    private fun stopProcessLifecycleObserver() {
        val observer = processLifecycleObserver ?: return
        ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        processLifecycleObserver = null
    }

    /**
     * Sends the most recent identify cached while replay was disabled.
     *
     * Clears the pending identify atomically to avoid races with concurrent identify calls,
     * and updates its sessionId to the current session before sending.
     */
    private fun flushPendingIdentify() {
        if (!this::sessionManager.isInitialized) return
        val exporterSnapshot = exporter ?: return

        val pending = synchronized(pendingIdentifyLock) {
            pendingIdentify.also { pendingIdentify = null }
        } ?: return

        val pendingUpdated = pending.copy(sessionId = sessionManager.getSessionId())
        instrumentationScope.launch {
            exporterSnapshot.sendIdentifyAndCache(pendingUpdated)
            eventQueue.send(pendingUpdated)
        }
    }

    suspend fun identifySession(
        ldContext: LDObserveContext,
        timestamp: Long = System.currentTimeMillis(),
        canonicalKeyOverride: String? = null
    ) {
        if (!this::sessionManager.isInitialized || exporter == null) {
            logger.warn("identifySession called before SessionReplayService was installed; skipping.")
            return
        }

        val sessionId = sessionManager.getSessionId()
        val event = IdentifyItemPayload.from(
            contextFriendlyName = observabilityContext.options.contextFriendlyName,
            resourceAttributes = observabilityContext.resourceAttributes,
            ldContext = ldContext,
            timestamp = timestamp,
            sessionId = sessionId,
            canonicalKeyOverride = canonicalKeyOverride
        )

        // When replay is disabled, cache the identify payload for later session init without sending it now.
        if (!_isEnabled.value) {
            synchronized(pendingIdentifyLock) {
                pendingIdentify = event
            }
            exporter?.cacheIdentify(event)
            return
        }

        // Sampled-out sessions are enabled but not recording; skip identify like tracks/collectors.
        if (!_isRunning.value) {
            return
        }

        synchronized(pendingIdentifyLock) {
            pendingIdentify = null
        }

        exporter?.sendIdentifyAndCache(event)
        eventQueue.send(event)
    }

    override fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        if (!completed) return

        val observeContext = buildObserveContext(contextKeys)
        instrumentationScope.launch {
            identifySession(observeContext, canonicalKeyOverride = canonicalKey)
        }
    }

    override fun afterTrack(name: String, metricValue: Double?, attributes: Attributes) {
        recordTrack(name, metricValue, attributes)
    }

    /**
     * Records a `Track` timeline event onto the active recording. Shared by the cross-platform
     * bridge ([com.launchdarkly.observability.replay.plugin.SessionReplayHookProxy]) and the
     * in-process track collector fed by Observability's single emitter.
     */
    private fun recordTrack(
        name: String,
        metricValue: Double?,
        attributes: Attributes,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!this::sessionManager.isInitialized || exporter == null) {
            logger.warn("track received before SessionReplayService was installed; skipping.")
            return
        }
        // Track events are timeline indicators on an active recording; skip when not running.
        if (!_isRunning.value) return

        val event = TrackItemPayload.from(
            eventKey = name,
            metricValue = metricValue,
            attributes = attributes,
            timestamp = timestamp,
            sessionId = sessionManager.getSessionId()
        )
        instrumentationScope.launch {
            eventQueue.send(event)
        }
    }

    private fun buildObserveContext(contextKeys: Map<String, String>): LDObserveContext {
        if (contextKeys.size == 1) {
            val (kind, key) = contextKeys.entries.first()
            return LDObserveContext.create(kind, key)
        }
        val subs = contextKeys.map { (kind, key) -> LDObserveContext.create(kind, key) }
        return LDObserveContext.createMulti(*subs.toTypedArray())
    }

}
