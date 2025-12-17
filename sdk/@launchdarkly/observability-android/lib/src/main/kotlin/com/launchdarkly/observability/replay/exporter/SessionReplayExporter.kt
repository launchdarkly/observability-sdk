package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.InteractionEvent
import com.launchdarkly.observability.replay.Position
import com.launchdarkly.observability.replay.capture.CaptureEvent
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.collections.iterator

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
    private val canvasDrawEntourage: Int = RRWEB_CANVAS_DRAW_ENTOURAGE
) : LogRecordExporter {
    private val coroutineScope = CoroutineScope(DispatcherProviderHolder.current.io + SupervisorJob())
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

    override fun export(logs: MutableCollection<LogRecordData>): CompletableResultCode {
        val resultCode = CompletableResultCode()

        coroutineScope.launch {
            // payloadIdCounter and pushedCanvasSize require to have a single reentrancy
            exportMutex.withLock {
            try {
                eventGenerator.generatingCanvasSize = pushedCanvasSize

                // Map to collect events by session ID
                val eventsBySession = mutableMapOf<String, MutableList<Event>>()
                // Set to track sessions that need initialization
                val sessionsNeedingInit = mutableSetOf<String>()

                // Don't assume logs are in chronological order, sorting helps avoid sending unnecessary full snapshots
                val sortedLogs = logs.sortedBy { it.observedTimestampEpochNanos }
                for (log in sortedLogs) {
                    when (EventDomain.fromString(log.attributes.get(AttributeKey.stringKey("event.domain")))) {
                        EventDomain.MEDIA -> {
                            val capture = extractCaptureFromLog(log)
                            if (capture != null) {
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
                                        width = capture.origWidth
                                    )
                                    // we need to send a full capture if the session id changes or there is a resize/orientation change
                                    val events = eventGenerator.generateCaptureFullEvents(capture)
                                    eventsBySession.getOrPut(capture.session) { mutableListOf() }
                                        .addAll(events)
                                } else {
                                    val events = eventGenerator.generateCaptureIncrementalEvents(capture)
                                    eventsBySession.getOrPut(capture.session) { mutableListOf() }
                                        .addAll(events)
                                }
                            }
                        }

                        EventDomain.INTERACTION -> {
                            val interaction = extractInteractionFromLog(log)
                            if (interaction != null) {
                                val events = eventGenerator.generateInteractionEvents(interaction)
                                eventsBySession.getOrPut(interaction.session) { mutableListOf() }.addAll(events)
                            }
                        }

                        EventDomain.IDENTIFY -> {
                            // Noop for identify events at this layer
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
                        try {
                            replayApiService.pushPayload(sessionId, "${nextPayloadId()}", events)

                            // flushes generating canvas size into pushedCanvasSize
                            pushedCanvasSize = eventGenerator.generatingCanvasSize
                        } catch (e: Exception) {
                            // TODO: O11Y-627 - pass in logger to implementation and use here
                            // Log.e(REPLAY_EXPORTER_NAME, "Error pushing payload for session $sessionId: ${e.message}", e)
                            resultCode.fail()
                            return@launch
                        }
                    }
                }

                // All captures processed successfully
                resultCode.succeed()
            } catch (e: Exception) {
                // TODO: O11Y-627 - pass in logger to implementation and use here
                // Log.e("SessionReplayExporter", "Error during export: ${e.message}", e)
                resultCode.fail()
            }
            }
        }

        return resultCode
    }

    override fun flush(): CompletableResultCode {
        // TODO: O11Y-621 - Handle flush
        return CompletableResultCode.ofSuccess()
    }

    suspend fun identifyEventAndUpdate(newIdentifyEvent: IdentifyItemPayload) {
        exportMutex.withLock {
            val sessionId = lastSeenState.sessionId
            if (sessionId != null) {
                replayApiService.identifyReplaySession(sessionId, newIdentifyEvent)
                identifyItemPayload = newIdentifyEvent
            }
        }
    }

    override fun shutdown(): CompletableResultCode {
        // TODO: O11Y-621 - Handle shutdown
        return CompletableResultCode.ofSuccess()
    }

    fun nextPayloadId(): Int {
        payloadIdCounter++
        return payloadIdCounter
    }

    // Returns null if unable to extract a valid capture from the log record
    private fun extractCaptureFromLog(log: LogRecordData): CaptureEvent? {
        val attributes = log.attributes
        val imageWidth = attributes.get(AttributeKey.longKey("image.width"))
        val imageHeight = attributes.get(AttributeKey.longKey("image.height"))
        val imageData = attributes.get(AttributeKey.stringKey("image.data"))
        val sessionId = attributes.get(AttributeKey.stringKey("session.id"))

        // Return null if any required attribute is missing. If this schema ever changes, consider that
        //  there may be cached data on disk on end user devices that may get ignored by this conditional.
        if (imageWidth == null || imageHeight == null || imageData == null || sessionId == null) {
            return null
        }

        return CaptureEvent(
            imageBase64 = imageData,
            origHeight = imageHeight.toInt(),
            origWidth = imageWidth.toInt(),
            timestamp = log.observedTimestampEpochNanos / 1_000_000, // Convert nanoseconds to milliseconds
            session = sessionId
        )
    }

    // Returns null if unable to extract a valid interaction from the log record
    private fun extractInteractionFromLog(log: LogRecordData): InteractionEvent? {
        val attributes = log.attributes
        val action = attributes.get(AttributeKey.longKey("android.action"))
        val coordsJson = attributes.get(AttributeKey.stringKey("screen.coords"))
        val sessionId = attributes.get(AttributeKey.stringKey("session.id"))

        // Return null if any required attribute is missing. If this schema ever changes, consider that
        // there may be disk cached data in production that may get ignored by this conditional.
        if (action == null || coordsJson == null || sessionId == null) {
            return null
        }

        // Parse the JSON array of positions
        val positions = try {
            val jsonElement = Json.parseToJsonElement(coordsJson)
            val jsonArray = jsonElement.jsonArray
            jsonArray.mapNotNull { positionElement ->
                val positionObj = positionElement.jsonObject
                val xPrimitive = positionObj["x"] as? JsonPrimitive
                val yPrimitive = positionObj["y"] as? JsonPrimitive
                val timestampPrimitive = positionObj["timestamp"] as? JsonPrimitive
                
                val x = xPrimitive?.content?.toIntOrNull()
                val y = yPrimitive?.content?.toIntOrNull()
                val timestamp = timestampPrimitive?.content?.toLongOrNull()
                
                if (x != null && y != null && timestamp != null) {
                    Position(x = x, y = y, timestamp = timestamp)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            // If JSON parsing fails, return null
            return null
        }

        // Return null if no valid positions were parsed
        if (positions.isEmpty()) {
            return null
        }

        return InteractionEvent(
            action = action.toInt(),
            positions = positions,
            session = sessionId
        )
    }

    // Generation methods have been moved to SessionReplayEventGenerator
}
