package com.launchdarkly.observability.replay.exporter

import android.view.MotionEvent
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.EventData
import com.launchdarkly.observability.replay.EventDataUnion
import com.launchdarkly.observability.replay.EventNode
import com.launchdarkly.observability.replay.EventType
import com.launchdarkly.observability.replay.InteractionEvent
import com.launchdarkly.observability.replay.NodeType
import com.launchdarkly.observability.replay.capture.CaptureEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Generates RRWeb-compatible events for session replay.
 *
 * Encapsulates generation state like sid sequencing and canvas size accounting.
 */
class SessionReplayEventGenerator(
    private val canvasDrawEntourage: Int
) {
    private var sidCounter = 0
    var generatingCanvasSize: Int = 0

    private fun nextSid(): Int {
        sidCounter++
        return sidCounter
    }

    /**
     * Generates events for an incremental capture. Used after [generateCaptureFullEvents] has already been called
     * for a previous capture in the same session.
     */
    fun generateCaptureIncrementalEvents(captureEvent: CaptureEvent): List<Event> {
        val eventsBatch = mutableListOf<Event>()

        val incrementalEvent = Event(
            type = EventType.INCREMENTAL_SNAPSHOT,
            timestamp = captureEvent.timestamp,
            sid = nextSid(),
            data = EventDataUnion.CustomEventDataWrapper(
                Json.parseToJsonElement(
                    """{"source":9,"id":6,"type":0,"commands":[{"property":"clearRect","args":[0,0,${captureEvent.origWidth},${captureEvent.origHeight}]},{"property":"drawImage","args":[{"rr_type":"ImageBitmap","args":[{"rr_type":"Blob","data":[{"rr_type":"ArrayBuffer","base64":"${captureEvent.imageBase64}"}],"type":"image/jpeg"}]},0,0,${captureEvent.origWidth},${captureEvent.origHeight}]}]}"""
                )
            )
        )
        generatingCanvasSize += captureEvent.imageBase64.length + canvasDrawEntourage
        eventsBatch.add(incrementalEvent)

        return eventsBatch
    }

    /**
     * Generates events for a full capture. May be invoked multiple times for a single session if a substantial
     * change occurs requiring a full capture to be sent.
     */
    fun generateCaptureFullEvents(captureEvent: CaptureEvent): List<Event> {
        val eventBatch = mutableListOf<Event>()

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

        // starting again canvas size
        generatingCanvasSize = captureEvent.imageBase64.length + canvasDrawEntourage
        eventBatch.add(snapShotEvent)

        val viewportEvent = Event(
            type = EventType.CUSTOM,
            timestamp = captureEvent.timestamp,
            sid = nextSid(),
            data = EventDataUnion.CustomEventDataWrapper(
                Json.parseToJsonElement(
                    """{"tag":"Viewport","payload":{"width":${captureEvent.origWidth},"height":${captureEvent.origHeight},"availWidth":${captureEvent.origWidth},"availHeight":${captureEvent.origHeight},"colorDepth":30,"pixelDepth":30,"orientation":0}}"""
                )
            )
        )
        eventBatch.add(viewportEvent)

        return eventBatch
    }


    /**
     * Generates events for a touch interaction.
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
                        data = EventDataUnion.CustomEventDataWrapper(
                            Json.parseToJsonElement("""{"source":2,"texts": [],"type":7,"id":6,"x":${firstPosition.x}, "y":${firstPosition.y}}""")
                        )
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
                        data = EventDataUnion.CustomEventDataWrapper(
                            Json.parseToJsonElement("""{"source":2,"texts": [],"type":9,"id":6,"x":${lastPosition.x}, "y":${lastPosition.y}}""")
                        )
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
                            data = EventDataUnion.CustomEventDataWrapper(
                                Json.parseToJsonElement("""{"positions": [{"id":6,"timeOffset":0,"x":${position.x},"y":${position.y}}], "source": 6}""")
                            )
                        )
                    )
                }
            }
        }

        return events
    }

    /**
     * Payload is a JSON string representing the user attributes map.
     */
    fun generateIdentifyEvent(identify: IdentifyItemPayload): Event? {
        val userJSONString = try {
            // Encode attributes map into a compact JSON string without requiring serializers
            buildJsonObject {
                identify.attributes.forEach { (k, v) -> put(k, v) }
            }.toString()
        } catch (_: Exception) {
            return null
        }

        val customData = buildJsonObject {
            put("tag", JsonPrimitive("Identify"))
            // Payload must be a JSON string per rrweb Custom event contract used by Swift
            put("payload", JsonPrimitive(userJSONString))
        }

        return Event(
            type = EventType.CUSTOM,
            timestamp = identify.timestamp,
            sid = nextSid(),
            data = EventDataUnion.CustomEventDataWrapper(customData)
        )
    }

    
}


