package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.capture.ExportFrame
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val injectedReplayApiService: SessionReplayApiService? = null,
    private val logger: LDLogger,
    private val canvasBufferLimit: Int = RRWEB_CANVAS_BUFFER_LIMIT,
    canvasDrawEntourage: Int = RRWEB_CANVAS_DRAW_ENTOURAGE
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
    // TODO: O11Y-624 - need to implement sid, payloadId reset when multiple sessions occur in one application process lifecycle.
    private var payloadIdCounter = 0
    private val eventGenerator = RRWebEventGenerator(canvasDrawEntourage)

    private data class LastCaptureState(
        val sessionId: String?,
        val height: Int,
        val width: Int,
    )

    private var lastCaptureState = LastCaptureState(sessionId = null, height = 0, width = 0)
    private var pushedCanvasSize = 0

    override suspend fun export(items: List<EventQueueItem>) {
        if (items.isEmpty()) return

        exportMutex.withLock {
            val lastCaptureSnapshot = lastCaptureState
            val payloadIdSnapshot = payloadIdCounter
            val pushedCanvasSnapshot = pushedCanvasSize
            val generatorSnapshot = eventGenerator.getState()

            try {
                eventGenerator.accumulatedCanvasSize = pushedCanvasSize

                // Map to collect events by session ID
                val eventsBySession = mutableMapOf<String, MutableList<Event>>()
                // Set to track sessions that need initialization
                val sessionsNeedingInit = mutableSetOf<String>()

                // Don't assume items are in chronological order
                val sortedItems = items.sortedBy { it.timestamp }
                for (item in sortedItems) {
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
                            payload.sessionId?.let { sessionId ->
                                eventGenerator.generateIdentifyEvent(payload)?.let { identifyEvent ->
                                    eventsBySession.getOrPut(sessionId) { mutableListOf() }.add(identifyEvent)
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
                    replayApiService.initializeReplaySession(organizationVerboseId, sessionId)
                    replayApiService.identifyReplaySession(sessionId, identifyItemPayload)
                    // TODO: O11Y-624 - handle request failures
                }

                // Send all events grouped by session
                for ((sessionId, events) in eventsBySession) {
                    if (events.isNotEmpty()) {
                        replayApiService.pushPayload(sessionId, "${nextPayloadId()}", events)
                        // flushes generating canvas size into pushedCanvasSize
                        pushedCanvasSize = eventGenerator.accumulatedCanvasSize
                    }
                }
            } catch (e: Exception) {
                // Roll back exporter state so retries regenerate identical events and payload ids.
                lastCaptureState = lastCaptureSnapshot
                payloadIdCounter = payloadIdSnapshot
                pushedCanvasSize = pushedCanvasSnapshot
                eventGenerator.restoreState(generatorSnapshot)
                throw e
            }
        }
    }

    suspend fun sendIdentifyAndCache(newIdentifyEvent: IdentifyItemPayload) {
        exportMutex.withLock {
            val sessionId = newIdentifyEvent.sessionId
            if (sessionId != null) {
                try {
                    replayApiService.identifyReplaySession(sessionId, newIdentifyEvent)
                    identifyItemPayload = newIdentifyEvent
                } catch (e: Exception) {
                    logger.error(e)
                }
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
            capture.originalSize.width != lastCaptureState.width ||
            eventGenerator.accumulatedCanvasSize >= canvasBufferLimit

        if (stateChanged) {
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
