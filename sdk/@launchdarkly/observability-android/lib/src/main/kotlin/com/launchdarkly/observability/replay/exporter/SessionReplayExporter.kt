package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.network.ErrorRecoverability
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.SessionReplayInitializationVerdict
import com.launchdarkly.observability.replay.capture.ExportFrame
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

// size limit of accumulated continues canvas operations on the RRWeb player
private const val RRWEB_CANVAS_BUFFER_LIMIT =  10_000_000 // ~10mb
private const val RRWEB_CANVAS_DRAW_ENTOURAGE = 300 // 300 bytes

/**
 * An [SessionReplayExporter] that can send session replay capture logs to the backend using RRWeb syntax
 * and GraphQL pushes for transport.
 *
 * @param organizationVerboseId the organization verbose id for the LaunchDarkly customer
 * @param backendUrl The backend URL the GraphQL operations
 * @param serviceName The service name
 * @param serviceVersion The service version
 * @param injectedReplayApiService Optional SessionReplayApiService for testing. If null, a default service will be created.
 * @param logger The logger for internal logging.
 */
class SessionReplayExporter(
    val organizationVerboseId: String,
    val backendUrl: String,
    val serviceName: String,
    val serviceVersion: String,
    val initialIdentifyItemPayload: IdentifyItemPayload,
    val title: String,
    private val injectedReplayApiService: SessionReplayApiService? = null,
    private val logger: ObserveLogger,
    private val canvasBufferLimit: Int = RRWEB_CANVAS_BUFFER_LIMIT,
    canvasDrawEntourage: Int = RRWEB_CANVAS_DRAW_ENTOURAGE,
    /**
     * The one-shot `Launch` breadcrumb, resolved during SDK start (before Session Replay subscribes)
     * and injected here so it can be folded into the first wake-up batch instead of being enqueued
     * early and racing session initialization. Owned by the exporter (guarded by [exportMutex]) so it
     * is never read from the shared context on the export thread.
     */
    initialAppLaunchItemPayload: AppLaunchItemPayload? = null,
    /**
     * Receives every recording verdict, so the service can withhold or stop screenshots. Invoked from
     * the export thread.
     */
    private val onInitializationVerdict: ((SessionReplayInitializationVerdict) -> Unit)? = null,
) : EventExporting {
    private val exportMutex = Mutex()

    private var graphqlClient: GraphQLClient = GraphQLClient(
        endpoint = backendUrl,
        logger = logger
    )
    private val replayApiService: SessionReplayApiService =
        injectedReplayApiService ?: SessionReplayApiService(
            graphqlClient = graphqlClient,
            serviceName = serviceName,
            serviceVersion = serviceVersion,
        )

    private var identifyItemPayload = initialIdentifyItemPayload
    // `val` (never reassigned) so its constructor write is safely published to the export coroutine
    // that reads it in `wakeUpEvents` (JMM final-field guarantee), independent of any lock.
    private val appLaunchItemPayload = initialAppLaunchItemPayload
    // TODO: O11Y-624 - need to implement sid, payloadId reset when multiple sessions occur in one application process lifecycle.
    private var payloadIdCounter = 0
    private var shouldWakeUpSession = true
    private val eventGenerator = RRWebEventGenerator(canvasDrawEntourage, title)

    /** Sessions the backend has accepted, so they are initialized at most once. Guarded by [exportMutex]. */
    private val initializedSessions = mutableSetOf<String>()

    /**
     * Set once the backend rejects the session unrecoverably. Recording cannot come back within this
     * process — a new attempt is made only on a fresh launch — so no further requests are made and queued
     * items are drained instead of retried. Read before taking [exportMutex], hence volatile.
     */
    @Volatile
    private var hasFailedUnrecoverably = false

    private data class LastCaptureState(
        val sessionId: String?,
        val height: Int,
        val width: Int,
    )

    private var lastCaptureState = LastCaptureState(sessionId = null, height = 0, width = 0)
    private var pushedCanvasSize = 0

    /**
     * Initializes [sessionId] without waiting for the first export batch, so an unrecoverable rejection
     * can stop capture at the very start of the launch. The verdict is delivered through
     * [onInitializationVerdict] and the export retry loop owns any retry, so failures are not rethrown.
     */
    suspend fun prepareSession(sessionId: String) {
        exportMutex.withLock {
            try {
                initializeSessionIfNeeded(sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e)
            }
        }
    }

    override suspend fun export(items: List<EventQueueItem>) {
        if (items.isEmpty()) return
        // Return without pushing so the queue drains: recording is over for this launch, and holding the
        // items would only keep the buffer full.
        if (hasFailedUnrecoverably) return

        exportMutex.withLock {
            // The refusal can also arrive while this batch waits for the lock, from the startup probe.
            if (hasFailedUnrecoverably) return@withLock

            val lastCaptureSnapshot = lastCaptureState
            val payloadIdSnapshot = payloadIdCounter
            val pushedCanvasSnapshot = pushedCanvasSize
            val shouldWakeUpSnapshot = shouldWakeUpSession
            val generatorSnapshot = eventGenerator.getState()

            try {
                eventGenerator.accumulatedCanvasSize = pushedCanvasSize

                // Map to collect events by session ID
                val eventsBySession = mutableMapOf<String, MutableList<Event>>()
                // Set to track sessions that need initialization
                val sessionsNeedingInit = mutableSetOf<String>()

                for (item in items) {
                    when (val payload = item.payload) {
                        is ImageItemPayload -> {
                            handleCapture(payload.capture, eventsBySession, sessionsNeedingInit)
                        }

                        is InteractionItemPayload -> {
                            val interaction = payload.interaction
                            val events = eventGenerator.generateInteractionEvents(interaction)
                            eventsBySession.getOrPut(interaction.session) { mutableListOf() }.addAll(events)
                        }

                        is IdentifyItemPayload -> {
                            val sessionId = payload.sessionId ?: lastCaptureSnapshot.sessionId
                            sessionId?.let { sessionId ->
                                eventGenerator.generateIdentifyEvent(payload)?.let { identifyEvent ->
                                    eventsBySession.getOrPut(sessionId) { mutableListOf() }.add(identifyEvent)
                                }
                            }
                        }

                        is TrackItemPayload -> {
                            val sessionId = payload.sessionId ?: lastCaptureSnapshot.sessionId
                            sessionId?.let { sessionId ->
                                eventGenerator.generateTrackEvent(payload)?.let { trackEvent ->
                                    eventsBySession.getOrPut(sessionId) { mutableListOf() }.add(trackEvent)
                                }
                            }
                        }

                        is NavigateItemPayload -> {
                            val sessionId = payload.sessionId ?: lastCaptureSnapshot.sessionId
                            sessionId?.let { sessionId ->
                                val navigateEvent = eventGenerator.generateNavigateEvent(payload)
                                eventsBySession.getOrPut(sessionId) { mutableListOf() }.add(navigateEvent)
                            }
                        }

                        is AppLifecycleItemPayload -> {
                            val sessionId = payload.sessionId ?: lastCaptureSnapshot.sessionId
                            sessionId?.let { sessionId ->
                                eventGenerator.generateAppLifecycleEvent(payload)?.let { lifecycleEvent ->
                                    eventsBySession.getOrPut(sessionId) { mutableListOf() }.add(lifecycleEvent)
                                }
                            }
                        }

                        is AppLaunchItemPayload -> {
                            val sessionId = payload.sessionId ?: lastCaptureSnapshot.sessionId
                            sessionId?.let { sessionId ->
                                eventGenerator.generateAppLaunchEvent(payload)?.let { launchEvent ->
                                    eventsBySession.getOrPut(sessionId) { mutableListOf() }.add(launchEvent)
                                }
                            }
                        }

                        else -> {
                            // Noop
                        }
                    }
                }

                // Initialize sessions that need it
                for (sessionId in sessionsNeedingInit) {
                    initializeSessionIfNeeded(sessionId)
                }

                // Send all events grouped by session
                for ((sessionId, events) in eventsBySession) {
                    if (events.isNotEmpty()) {
                        // A refusal can also land part-way through a batch, from a wake-up push whose
                        // failure is swallowed below, and a refused session accepts no payloads. What is
                        // left of the batch is dropped rather than retried, like any batch after a refusal.
                        if (hasFailedUnrecoverably) return@withLock

                        // Events can belong to a session no capture has been taken of yet — while
                        // screenshots are withheld, or from a touch that precedes the first frame — and the
                        // backend only accepts payloads for an initialized session.
                        initializeSessionIfNeeded(sessionId)
                        replayApiService.pushPayload(sessionId, "${nextPayloadId()}", events)
                        // flushes generating canvas size into pushedCanvasSize
                        pushedCanvasSize = eventGenerator.accumulatedCanvasSize

                        wakeUpEvents(events, sessionId)
                    }
                }

            } catch (e: Exception) {
                // Roll back exporter state so retries regenerate identical events and payload ids.
                lastCaptureState = lastCaptureSnapshot
                payloadIdCounter = payloadIdSnapshot
                pushedCanvasSize = pushedCanvasSnapshot
                shouldWakeUpSession = shouldWakeUpSnapshot
                eventGenerator.restoreState(generatorSnapshot)
                if (e is CancellationException) throw e

                reportIfUnrecoverable(e)
                // A refusal is not a retryable export failure: rethrowing it would have the worker back
                // off (up to a minute) still holding a batch nothing accepts any more, so the drain would
                // only start once that backoff expires.
                if (hasFailedUnrecoverably) return@withLock

                throw e
            }
        }
    }

    /**
     * Initializes [sessionId] once, and reports what the backend made of it. A failure leaves the session
     * uninitialized so the next attempt retries both calls, and is rethrown for the export retry loop.
     */
    private suspend fun initializeSessionIfNeeded(sessionId: String) {
        if (hasFailedUnrecoverably || !initializedSessions.add(sessionId)) return

        try {
            replayApiService.initializeReplaySession(organizationVerboseId, sessionId)
            // Accepting the session is the recording verdict on its own: reporting it here rather than
            // after `identifyReplaySession` keeps a transient identify failure from withholding screenshots.
            // An unrecoverable identify failure still refuses the launch, through the catch below.
            report(SessionReplayInitializationVerdict.Allowed)
            replayApiService.identifyReplaySession(sessionId, identifyItemPayload)
        } catch (e: Exception) {
            initializedSessions.remove(sessionId)
            reportIfUnrecoverable(e)
            throw e
        }
    }

    /**
     * Classifies a failed request: recoverable errors are left to the export retry loop (items stay
     * buffered until the queue overflows), while an unrecoverable one ends recording for this launch.
     */
    private fun reportIfUnrecoverable(error: Throwable) {
        if (hasFailedUnrecoverably || ErrorRecoverability.isErrorRecoverable(error)) return

        hasFailedUnrecoverably = true
        logger.error("Session replay stopped, unrecoverable error", error)
        report(SessionReplayInitializationVerdict.Unrecoverable(error.message ?: error.toString()))
    }

    private fun report(verdict: SessionReplayInitializationVerdict) {
        onInitializationVerdict?.invoke(verdict)
    }

    private suspend fun wakeUpEvents(
        events: MutableList<Event>,
        sessionId: String
    ) {
        try {
            if (shouldWakeUpSession) {
                val lastEventTimestamp = events.lastOrNull()?.timestamp ?: 0L
                val wakeUpEvents = eventGenerator.generateWakeUpEvents(lastEventTimestamp, appLaunchItemPayload)
                if (wakeUpEvents.isNotEmpty()) {
                    // we need a separate payload to wake up player
                    replayApiService.pushPayload(sessionId, "${nextPayloadId()}", wakeUpEvents)
                    shouldWakeUpSession = false
                }
            }
        } catch (e: Exception) {
            // put wake up in the try/catch do not break buffering logic
            logger.error(e)
            reportIfUnrecoverable(e)
        }
    }

    suspend fun sendIdentifyAndCache(newIdentifyEvent: IdentifyItemPayload) {
        exportMutex.withLock {
            // The identify hook stays registered after recording ends, so without this the abandoned
            // session would keep receiving identify calls.
            if (hasFailedUnrecoverably) return@withLock

            val sessionId = newIdentifyEvent.sessionId ?: return@withLock

            // The backend only accepts identify for a session it has already accepted, and the startup
            // probe can still be in flight - identifying now would be answered with an error that a
            // refusal cannot be told apart from. Caching is enough: initialization sends the payload it
            // finds here, so the latest one reaches the backend either way.
            if (sessionId !in initializedSessions) {
                identifyItemPayload = newIdentifyEvent
                return@withLock
            }

            try {
                replayApiService.identifyReplaySession(sessionId, newIdentifyEvent)
                identifyItemPayload = newIdentifyEvent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // There is nothing to retry an identify with, so the failure is only logged - but a
                // refusal ends recording here like it does for any other request.
                logger.error(e)
                reportIfUnrecoverable(e)
            }
        }
    }

    internal suspend fun cacheIdentify(newIdentifyEvent: IdentifyItemPayload) {
        exportMutex.withLock {
            identifyItemPayload = newIdentifyEvent
        }
    }

    fun nextPayloadId(): Int {
        payloadIdCounter++
        return payloadIdCounter
    }

    private fun handleCapture(
        capture: ExportFrame,
        eventsBySession: MutableMap<String, MutableList<Event>>,
        sessionsNeedingInit: MutableSet<String>,
    ) {
        if (capture.session != lastCaptureState.sessionId) {
            sessionsNeedingInit.add(capture.session)
        }

        val stateChanged = capture.session != lastCaptureState.sessionId ||
            capture.originalSize.height != lastCaptureState.height ||
            capture.originalSize.width != lastCaptureState.width

        val shouldForceFullByCanvasLimit =
            eventGenerator.accumulatedCanvasSize >= canvasBufferLimit && capture.isKeyframe

        if (stateChanged || shouldForceFullByCanvasLimit) {
            lastCaptureState = LastCaptureState(
                sessionId = capture.session,
                height = capture.originalSize.height,
                width = capture.originalSize.width,
            )
            // we need to send a full capture if the session id changes or there is a resize/orientation change
            val events = eventGenerator.generateCaptureFullEvents(capture)
            eventsBySession.getOrPut(capture.session) { mutableListOf() }.addAll(events)
        } else {
            val events = eventGenerator.generateCaptureIncrementalEvents(capture)
            eventsBySession.getOrPut(capture.session) { mutableListOf() }.addAll(events)
        }
    }
}
