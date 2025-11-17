package com.launchdarkly.observability.replay

import android.view.MotionEvent
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.network.GraphQLClient
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val REPLAY_EXPORTER_NAME = "RRwebGraphQLReplayLogExporter"

/**
 * An [LogRecordExporter] that can send session replay capture logs to the backend using RRWeb syntax
 * and GraphQL pushes for transport.
 *
 * @param organizationVerboseId the organization verbose id for the LaunchDarkly customer
 * @param backendUrl The backend URL the GraphQL operations
 * @param serviceName The service name
 * @param serviceVersion The service version
 * @param injectedReplayApiService Optional SessionReplayApiService for testing. If null, a default service will be created.
 */
class RRwebGraphQLReplayLogExporter(
    val organizationVerboseId: String,
    val backendUrl: String,
    val serviceName: String,
    val serviceVersion: String,
    private val injectedReplayApiService: SessionReplayApiService? = null
) : LogRecordExporter {
    private val coroutineScope = CoroutineScope(DispatcherProviderHolder.current.io + SupervisorJob())

    private var graphqlClient: GraphQLClient = GraphQLClient(backendUrl)
    private val replayApiService: SessionReplayApiService =
        injectedReplayApiService ?: SessionReplayApiService(
            graphqlClient = graphqlClient,
            serviceName = serviceName,
            serviceVersion = serviceVersion,
        )

    // TODO: O11Y-624 - need to implement sid, payloadId reset when multiple sessions occur in one application process lifecycle.
    private var sidCounter = 0
    private var payloadIdCounter = 0

    private data class LastSeenState(
        val sessionId: String?,
        val height: Int,
        val width: Int,
    )

    private var lastSeenState = LastSeenState(sessionId = null, height = 0, width = 0)

    override fun export(logs: MutableCollection<LogRecordData>): CompletableResultCode {
        val resultCode = CompletableResultCode()

        coroutineScope.launch {
            try {
                // Map to collect events by session ID
                val eventsBySession = mutableMapOf<String, MutableList<Event>>()
                // Set to track sessions that need initialization
                val sessionsNeedingInit = mutableSetOf<String>()

                // Don't assume logs are in chronological order, sorting helps avoid sending unnecessary full snapshots
                val sortedLogs = logs.sortedBy { it.observedTimestampEpochNanos }
                for (log in sortedLogs) {
                    when (log.attributes.get(AttributeKey.stringKey("event.domain"))) {
                        "media" -> {
                            val capture = extractCaptureFromLog(log)
                            if (capture != null) {
                                if (capture.session != lastSeenState.sessionId) {
                                    sessionsNeedingInit.add(capture.session)
                                }

                                val stateChanged = capture.session != lastSeenState.sessionId ||
                                        capture.origHeight != lastSeenState.height ||
                                        capture.origWidth != lastSeenState.width

                                if (stateChanged) {
                                    lastSeenState = LastSeenState(
                                        sessionId = capture.session,
                                        height = capture.origHeight,
                                        width = capture.origWidth
                                    )
                                    // we need to send a full capture if the session id changes or there is a resize/orientation change
                                    val events = generateCaptureFullEvents(capture)
                                    eventsBySession.getOrPut(capture.session) { mutableListOf() }
                                        .addAll(events)
                                } else {
                                    val events = generateCaptureIncrementalEvents(capture)
                                    eventsBySession.getOrPut(capture.session) { mutableListOf() }
                                        .addAll(events)
                                }
                            }
                        }

                        "interaction" -> {
                            val interaction = extractInteractionFromLog(log)
                            if (interaction != null) {
                                val events = generateInteractionEvents(interaction)
                                eventsBySession.getOrPut(interaction.session) { mutableListOf() }.addAll(events)
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
                    replayApiService.identifyReplaySession(sessionId)
                    // TODO: O11Y-624 - handle request failures
                }

                // Send all events grouped by session
                for ((sessionId, events) in eventsBySession) {
                    if (events.isNotEmpty()) {
                        try {
                            replayApiService.pushPayload(sessionId, "${nextPayloadId()}", events)
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
                // Log.e("RRwebGraphQLReplayLogExporter", "Error during export: ${e.message}", e)
                resultCode.fail()
            }
        }

        return resultCode
    }

    override fun flush(): CompletableResultCode {
        // TODO: O11Y-621 - Handle flush
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        // TODO: O11Y-621 - Handle shutdown
        return CompletableResultCode.ofSuccess()
    }

    fun nextSid(): Int {
        sidCounter++
        return sidCounter
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

    /**
     * Generates events for an incremental capture. Used after [generateCaptureFullEvents] has already been called for a previous capture in the same session.
     *
     * @param captureEvent the capture to generate events for
     * @return list of events for the incremental capture
     */
    fun generateCaptureIncrementalEvents(captureEvent: CaptureEvent): List<Event> {
        val eventsBatch = mutableListOf<Event>()

        // TODO: O11Y-625 - optimize JSON usage for performance since this region of code is essentially static

        val incrementalEvent = Event(
            type = EventType.INCREMENTAL_SNAPSHOT,
            timestamp = captureEvent.timestamp,
            sid = nextSid(),
            data = EventDataUnion.CustomEventDataWrapper(
                Json.parseToJsonElement("""{"source":9,"id":6,"type":0,"commands":[{"property":"clearRect","args":[0,0,${captureEvent.origWidth},${captureEvent.origHeight}]},{"property":"drawImage","args":[{"rr_type":"ImageBitmap","args":[{"rr_type":"Blob","data":[{"rr_type":"ArrayBuffer","base64":"${captureEvent.imageBase64}"}],"type":"image/jpeg"}]},0,0,${captureEvent.origWidth},${captureEvent.origHeight}]}]}""")
            )
        )
        eventsBatch.add(incrementalEvent)

        return eventsBatch
    }

    /**
     * Generates events for a full capture. May be invoked multiple times for a single session if a substantial
     * change occurs requiring a full capture to be sent.
     *
     * @param captureEvent the capture to generate events for
     * @return list of events for the full capture
     */
    fun generateCaptureFullEvents(captureEvent: CaptureEvent): List<Event> {
        val eventBatch = mutableListOf<Event>()

        // TODO: O11Y-625 - optimize JSON usage for performance since this region of code is essentially static

        val metaEvent = Event(
            type = EventType.META,
            timestamp = captureEvent.timestamp,
            sid = nextSid(),
            data = EventDataUnion.StandardEventData(
                EventData(
                    width = captureEvent.origWidth,
                    height = captureEvent.origHeight,
                )
            ),
        )
        eventBatch.add(metaEvent)

        val snapShotEvent = Event(
            type = EventType.FULL_SNAPSHOT,
            timestamp = captureEvent.timestamp,
            sid = nextSid(),
            data = EventDataUnion.StandardEventData(
                EventData(
                    node = EventNode(
                        id = 1,
                        type = NodeType.DOCUMENT,
                        childNodes = listOf(
                            EventNode(
                                id = 2,
                                type = NodeType.DOCUMENT_TYPE,
                                name = "html",
                            ),
                            EventNode(
                                id = 3,
                                type = NodeType.ELEMENT,
                                tagName = "html",
                                attributes = mapOf("lang" to "en"),
                                childNodes = listOf(
                                    EventNode(
                                        id = 4,
                                        type = NodeType.ELEMENT,
                                        tagName = "head",
                                        attributes = emptyMap(),
                                    ),
                                    EventNode(
                                        id = 5,
                                        type = NodeType.ELEMENT,
                                        tagName = "body",
                                        attributes = emptyMap(),
                                        childNodes = listOf(
                                            EventNode(
                                                id = 6,
                                                type = NodeType.ELEMENT,
                                                tagName = "canvas",
                                                attributes = mapOf(
                                                    "rr_dataURL" to "data:image/jpeg;base64,${captureEvent.imageBase64}",
                                                    "width" to "${captureEvent.origWidth}",
                                                    "height" to "${captureEvent.origHeight}"
                                                ),
                                                childNodes = listOf(),
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                    ),
                )
            ),
        )
        eventBatch.add(snapShotEvent)

        val viewportEvent = Event(
            type = EventType.CUSTOM,
            timestamp = captureEvent.timestamp,
            sid = nextSid(),
            data = EventDataUnion.CustomEventDataWrapper(
                Json.parseToJsonElement("""{"tag":"Viewport","payload":{"width":${captureEvent.origWidth},"height":${captureEvent.origHeight},"availWidth":${captureEvent.origWidth},"availHeight":${captureEvent.origHeight},"colorDepth":30,"pixelDepth":30,"orientation":0}}""")
            )
        )
        eventBatch.add(viewportEvent)

        return eventBatch
    }

    /**
     * Generates events for a touch interaction.
     *
     * @param interactionEvent the interaction to generate events for
     * @return list of events for the interaction
     */
    fun generateInteractionEvents(interactionEvent: InteractionEvent): List<Event> {
        val events = mutableListOf<Event>()

        when (interactionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                val firstPosition = interactionEvent.positions.first()
                events.add(
                    Event(
                        type = EventType.INCREMENTAL_SNAPSHOT,
                        timestamp = firstPosition.timestamp,
                        sid = nextSid(),
                        data = EventDataUnion.CustomEventDataWrapper(Json.parseToJsonElement("""{"source":2,"texts": [],"type":7,"id":6,"x":${firstPosition.x}, "y":${firstPosition.y}}"""))
                    )
                )
                events.add(
                    Event(
                        type = EventType.CUSTOM,
                        timestamp = firstPosition.timestamp,
                        sid = nextSid(),
                        data = EventDataUnion.CustomEventDataWrapper(
                            Json.parseToJsonElement("""{"tag":"Click","payload":{"clickTarget":"BogusName","clickTextContent":"","clickSelector":"view"}}""")
                        )
                    )
                )
            }

            MotionEvent.ACTION_UP -> { // CANCEL is not here because UP and CANCEL are merged to UP in interaction source.
                val lastPosition = interactionEvent.positions.last()
                events.add(
                    Event(
                        type = EventType.INCREMENTAL_SNAPSHOT,
                        timestamp = lastPosition.timestamp,
                        sid = nextSid(),
                        data = EventDataUnion.CustomEventDataWrapper(Json.parseToJsonElement("""{"source":2,"texts": [],"type":9,"id":6,"x":${lastPosition.x}, "y":${lastPosition.y}}"""))
                    )
                )
            }

            MotionEvent.ACTION_MOVE -> {
                // Generate one event per position. Each positionsJson will only contain one position.
                interactionEvent.positions.forEach { position ->
                    events.add(
                        Event(
                            type = EventType.INCREMENTAL_SNAPSHOT,
                            timestamp = position.timestamp,
                            sid = nextSid(),
                            data = EventDataUnion.CustomEventDataWrapper(Json.parseToJsonElement("""{"positions": [{"id":6,"timeOffset":0,"x":${position.x},"y":${position.y}}], "source": 6}"""))
                        )
                    )
                }
            }
        }

        return events
    }
}
