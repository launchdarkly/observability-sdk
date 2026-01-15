package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.capture.CaptureEvent
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
 */
class SessionReplayExporter(
    val organizationVerboseId: String,
    val backendUrl: String,
    val serviceName: String,
    val serviceVersion: String,
    val initialIdentifyItemPayload: IdentifyItemPayload,
    private val injectedReplayApiService: SessionReplayApiService? = null,
    private val canvasBufferLimit: Int = RRWEB_CANVAS_BUFFER_LIMIT,
    canvasDrawEntourage: Int = RRWEB_CANVAS_DRAW_ENTOURAGE
) : EventExporting {
    private val exportMutex = Mutex()

    private var graphqlClient: GraphQLClient = GraphQLClient(backendUrl)
    private val replayApiService: SessionReplayApiService =
        injectedReplayApiService ?: SessionReplayApiService(
            graphqlClient = graphqlClient,
            serviceName = serviceName,
            serviceVersion = serviceVersion,
        )

    private var identifyItemPayload = initialIdentifyItemPayload
    // TODO: O11Y-624 - need to implement sid, payloadId reset when multiple sessions occur in one application process lifecycle.
    private var payloadIdCounter = 0
    private val eventGenerator = SessionReplayEventGenerator(canvasDrawEntourage)

    private data class LastSeenState(
        val sessionId: String?,
        val height: Int,
        val width: Int,
    )

    private var lastSeenState = LastSeenState(sessionId = null, height = 0, width = 0)
    private var pushedCanvasSize = 0

    override suspend fun export(items: List<EventQueueItem>) {
        if (items.isEmpty()) return

        exportMutex.withLock {
            try {
                eventGenerator.generatingCanvasSize = pushedCanvasSize

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
                        pushedCanvasSize = eventGenerator.generatingCanvasSize
                    }
                }
            } catch (e: Exception) {
                // TODO: O11Y-627 - pass in logger to implementation and use here
                throw e
            }
        }
    }

    suspend fun identifyEventAndUpdate(newIdentifyEvent: IdentifyItemPayload) {
        exportMutex.withLock {
            val sessionId = newIdentifyEvent.sessionId
            if (sessionId != null) {
                replayApiService.identifyReplaySession(sessionId, newIdentifyEvent)
                identifyItemPayload = newIdentifyEvent
            }
        }
    }

    fun nextPayloadId(): Int {
        payloadIdCounter++
        return payloadIdCounter
    }

    private fun handleCapture(
        capture: CaptureEvent,
        eventsBySession: MutableMap<String, MutableList<Event>>,
        sessionsNeedingInit: MutableSet<String>,
    ) {
        if (capture.session != lastSeenState.sessionId) {
            sessionsNeedingInit.add(capture.session)
        }

        val stateChanged = capture.session != lastSeenState.sessionId ||
            capture.origHeight != lastSeenState.height ||
            capture.origWidth != lastSeenState.width ||
            eventGenerator.generatingCanvasSize >= canvasBufferLimit

        if (stateChanged) {
            lastSeenState = LastSeenState(
                sessionId = capture.session,
                height = capture.origHeight,
                width = capture.origWidth,
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
