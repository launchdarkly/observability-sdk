package com.launchdarkly.observability.replay

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.observability.replay.capture.CaptureSource
import com.launchdarkly.observability.replay.exporter.IdentifyItemPayload
import com.launchdarkly.observability.replay.exporter.ImageItemPayload
import com.launchdarkly.observability.replay.exporter.InteractionItemPayload
import com.launchdarkly.observability.replay.exporter.SessionReplayExporter
import com.launchdarkly.observability.replay.transport.BatchWorker
import com.launchdarkly.observability.replay.transport.EventQueue
import com.launchdarkly.observability.sdk.ReplayControl
import com.launchdarkly.sdk.LDContext
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.session.SessionManager
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

private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability.replay"

/**
 * Provides session replay instrumentation. Session replays that are sampled will appear on the LaunchDarkly dashboard.
 *
 * @param options Configuration options for replay behavior including privacy settings and capture interval
 * @param observabilityContext Shared context provided by the Observability plugin
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
class ReplayInstrumentation(
    private val options: ReplayOptions = ReplayOptions(),
    private val observabilityContext: ObservabilityContext
) : LDExtendedInstrumentation, ReplayControl {

    private lateinit var sessionManager: SessionManager
    private val logger: LDLogger = observabilityContext.logger
    private val eventQueue = EventQueue()
    private val batchWorker = BatchWorker(eventQueue, logger)
    private var captureSource: CaptureSource? = null
    private var interactionSource: InteractionSource? = null
    private val instrumentationScope = CoroutineScope(DispatcherProviderHolder.current.default + SupervisorJob())
    private var captureJob: Job? = null
    private val shouldCapture = MutableStateFlow(false)
    private val isEnabled = MutableStateFlow(options.enabled)
    private var processLifecycleObserver: DefaultLifecycleObserver? = null
    private var isInstalled: Boolean = false
    private var exporter: SessionReplayExporter? = null
    private val pendingIdentifyLock = Any()
    private var pendingIdentify: IdentifyItemPayload? = null

    override val name: String = INSTRUMENTATION_SCOPE_NAME

    override fun install(ctx: InstallationContext) {
        // If already installed, do nothing. This prevents duplicating collectors and lifecycle listeners.
        // We should refactor this if we want to support multiple sessions and install the instrumentation more than once
        if (isInstalled) return

        sessionManager = ctx.sessionManager
        captureSource = CaptureSource(
            sessionManager = ctx.sessionManager,
            options = options,
            logger = observabilityContext.logger
        )
        interactionSource = InteractionSource(ctx.sessionManager, options.scale)

        val initialIdentifyItemPayload = IdentifyItemPayload.from(
            contextFriendlyName = observabilityContext.options.contextFriendlyName,
            resourceAttributes = observabilityContext.options.resourceAttributes,
            sessionId = null // initial payload is not part SR RRWeb event
        )
        val exporter = SessionReplayExporter(
            organizationVerboseId = observabilityContext.sdkKey, // SDK key used as organization ID intentionally
            backendUrl = observabilityContext.options.backendUrl,
            serviceName = observabilityContext.options.serviceName,
            serviceVersion = observabilityContext.options.serviceVersion,
            initialIdentifyItemPayload = initialIdentifyItemPayload,
            logger = logger
        )
        this@ReplayInstrumentation.exporter = exporter
        batchWorker.addExporter(exporter)
        batchWorker.start()

        startCollectors()
        startCaptureStateObserver()
        startProcessLifecycleObserver()

        interactionSource?.attachToApplication(ctx.application)

        isInstalled = true
    }

    private fun startCollectors() {
        // Images collector
        instrumentationScope.launch {
            captureSource?.captureFlow?.collect { capture ->
                if (!isEnabled.value) return@collect
                eventQueue.send(ImageItemPayload(capture))
            }
        }

        // Interactions collector
        instrumentationScope.launch {
            interactionSource?.captureFlow?.collect { interaction ->
                if (!isEnabled.value) return@collect
                eventQueue.send(InteractionItemPayload(interaction))
            }
        }
    }

    /**
     * Observes [shouldCapture] state changes and synchronizes the capture loop state.
     * Skips redundant operations if the desired state already matches the current state.
     */
    private fun startCaptureStateObserver() {
        instrumentationScope.launch {
            combine(shouldCapture, isEnabled) { shouldRun, enabled -> shouldRun && enabled }
                .collect { shouldRun ->
                    val running = captureJob?.isActive == true
                    if (shouldRun == running) return@collect
                    if (shouldRun) doRunCapture() else doPauseCapture()
                }
        }
    }

    private suspend fun doRunCapture() {
        captureJob?.cancelAndJoin()
        captureJob = instrumentationScope.launch {
            logger.debug("Session replay capture running")
            while (isActive) {
                try {
                    captureSource?.captureNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    logger.error("Capture paused due to OOM", e)
                    shouldCapture.value = false
                    return@launch
                } catch (e: Exception) {
                    logger.error("Capture failed", e)
                }
                delay(options.capturePeriodMillis)
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

    override fun start() {
        isEnabled.value = true
        flushPendingIdentify()
    }

    override fun stop() {
        isEnabled.value = false
    }

    override fun flush() {
        batchWorker.flush()
    }

    private fun startProcessLifecycleObserver() {
        if (processLifecycleObserver != null) return

        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                runCapture()
            }

            override fun onStop(owner: LifecycleOwner) {
                pauseCapture()
                batchWorker.flush()
            }
        }

        processLifecycleObserver = observer
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)

        // Ensure we don't miss the initial foreground state when installing late.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            runCapture()
        }
    }

    // TODO: O11Y-621 - This should be called somewhere (Probably inside InstrumentationManager.kt) to shutdown the instrumentation.
    fun shutdown() {
        pauseCapture()
        stopProcessLifecycleObserver()
        batchWorker.stop()
        instrumentationScope.cancel()
        interactionSource?.detachFromApplication(observabilityContext.application)
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
        ldContext: LDContext,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!this::sessionManager.isInitialized || exporter == null) {
            logger.warn("identifySession called before ReplayInstrumentation was installed; skipping.")
            return
        }

        val sessionId = sessionManager.getSessionId()
        val event = IdentifyItemPayload.from(
            contextFriendlyName = observabilityContext.options.contextFriendlyName,
            resourceAttributes = observabilityContext.options.resourceAttributes,
            ldContext = ldContext,
            timestamp = timestamp,
            sessionId = sessionId
        )

        // When replay is disabled, cache the identify payload for later session init without sending it now.
        if (!isEnabled.value) {
            synchronized(pendingIdentifyLock) {
                pendingIdentify = event
            }
            exporter?.cacheIdentify(event)
            return
        }

        synchronized(pendingIdentifyLock) {
            pendingIdentify = null
        }
        exporter?.sendIdentifyAndCache(event)
        eventQueue.send(event)
    }

    override fun getLoggerScopeName(): String = INSTRUMENTATION_SCOPE_NAME
}
