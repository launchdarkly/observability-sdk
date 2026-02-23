package com.launchdarkly.observability.replay.exporter

import android.view.MotionEvent
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.Addition
import com.launchdarkly.observability.replay.EventData
import com.launchdarkly.observability.replay.EventDataUnion
import com.launchdarkly.observability.replay.EventNode
import com.launchdarkly.observability.replay.EventType
import com.launchdarkly.observability.replay.IncrementalSource
import com.launchdarkly.observability.replay.InteractionEvent
import com.launchdarkly.observability.replay.NodeType
import com.launchdarkly.observability.replay.Removal
import com.launchdarkly.observability.replay.RRWebCustomDataTag
import com.launchdarkly.observability.replay.RRWebIncrementalSource
import com.launchdarkly.observability.replay.RRWebMouseInteraction
import com.launchdarkly.observability.replay.capture.CaptureEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Generates RRWeb-compatible events for session replay.
 *
 * Encapsulates generation state like sid sequencing and canvas size accounting.
 */
class RRWebEventGenerator(
    private val canvasDrawEntourage: Int
) {
    companion object {
        private const val RRWEB_DOCUMENT_PADDING = 11
        private const val RRWEB_INITIAL_IMAGE_NODE_ID = 6
        private const val RRWEB_BODY_NODE_ID = 5
        private const val DOM_HTML = "html"
        private const val DOM_HEAD = "head"
        private const val DOM_BODY = "body"
        private const val DOM_LANG = "lang"
        private const val DOM_LANG_EN = "en"
        private const val DOM_STYLE = "style"
        private const val DOM_BODY_STYLE = "position:relative;"
        private const val IMAGE_MIME_TYPE = "image/webp"
        private const val CLICK_TARGET_VALUE = ""
        private const val CLICK_TEXT_CONTENT_VALUE = ""
        private const val CLICK_SELECTOR_VALUE = "view"
    }

    /**
     * Sequence ID for the events being generated.
     * Each event in a session needs a unique, monotonically increasing "sid".
     * This is incremented by [nextSid] for each new event.
     */
    private var lastSid = 0
    var accumulatedCanvasSize: Int = 0
    private var currentImageNodeId: Int = RRWEB_INITIAL_IMAGE_NODE_ID
    private var nextDynamicNodeId: Int = RRWEB_INITIAL_IMAGE_NODE_ID + 1

    data class State(
        val lastSid: Int = 0,
        val generatingCanvasSize: Int = 0,
        val currentImageNodeId: Int = RRWEB_INITIAL_IMAGE_NODE_ID,
        val nextDynamicNodeId: Int = RRWEB_INITIAL_IMAGE_NODE_ID + 1,
    )

    private fun nextSid(): Int {
        lastSid++
        return lastSid
    }

    fun getState(): State = State(
        lastSid = lastSid,
        generatingCanvasSize = accumulatedCanvasSize,
        currentImageNodeId = currentImageNodeId,
        nextDynamicNodeId = nextDynamicNodeId,
    )

    fun restoreState(state: State) {
        lastSid = state.lastSid
        accumulatedCanvasSize = state.generatingCanvasSize
        currentImageNodeId = state.currentImageNodeId
        nextDynamicNodeId = state.nextDynamicNodeId
    }

    /**
     * Generates events for an incremental capture. Used after [generateCaptureFullEvents] has already been called
     * for a previous capture in the same session.
     */
    fun generateCaptureIncrementalEvents(captureEvent: CaptureEvent): List<Event> {
        val dataUrl = "data:$IMAGE_MIME_TYPE;base64,${captureEvent.imageBase64}"
        val previousImageNodeId = currentImageNodeId
        val newImageNodeId = nextDynamicNodeId++

        val imageNode = EventNode(
            id = newImageNodeId,
            type = NodeType.ELEMENT,
            tagName = "img",
            attributes = mapOf(
                "src" to dataUrl,
                "width" to "${captureEvent.origWidth}",
                "height" to "${captureEvent.origHeight}",
                "style" to "position:absolute;left:0px;top:0px;pointer-events:none;",
            ),
            childNodes = emptyList(),
        )

        val mutationEvent = Event(
            type = EventType.INCREMENTAL_SNAPSHOT,
            timestamp = captureEvent.timestamp,
            sid = nextSid(),
            data = EventDataUnion.StandardEventData(
                EventData(
                    source = IncrementalSource.MUTATION,
                    adds = listOf(
                        Addition(
                            parentId = RRWEB_BODY_NODE_ID,
                            nextId = null,
                            node = imageNode,
                        )
                    ),
                    removes = listOf(
                        Removal(
                            parentId = RRWEB_BODY_NODE_ID,
                            id = previousImageNodeId,
                        )
                    ),
                )
            ),
        )

        currentImageNodeId = newImageNodeId
        accumulatedCanvasSize += dataUrl.length + canvasDrawEntourage
        return listOf(mutationEvent)
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
                    width = captureEvent.origWidth + RRWEB_DOCUMENT_PADDING * 2,
                    height = captureEvent.origHeight + RRWEB_DOCUMENT_PADDING * 2,
                    )
            ),
        )
        eventBatch.add(metaEvent)

        val snapshotEvent = Event(
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
                                tagName = DOM_HTML,
                                attributes = mapOf(DOM_LANG to DOM_LANG_EN),
                                childNodes = listOf(
                                    EventNode(
                                        id = 4,
                                        type = NodeType.ELEMENT,
                                        tagName = DOM_HEAD,
                                        attributes = emptyMap(),
                                    ),
                                    EventNode(
                                        id = 5,
                                        type = NodeType.ELEMENT,
                                        tagName = DOM_BODY,
                                        attributes = mapOf(DOM_STYLE to DOM_BODY_STYLE),
                                        childNodes = listOf(
                                            EventNode(
                                                id = RRWEB_INITIAL_IMAGE_NODE_ID,
                                                type = NodeType.ELEMENT,
                                                tagName = "img",
                                                attributes = mapOf(
                                                    "rr_dataURL" to "data:$IMAGE_MIME_TYPE;base64,${captureEvent.imageBase64}",
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
        accumulatedCanvasSize = captureEvent.imageBase64.length + canvasDrawEntourage
        currentImageNodeId = RRWEB_INITIAL_IMAGE_NODE_ID
        nextDynamicNodeId = RRWEB_INITIAL_IMAGE_NODE_ID + 1
        eventBatch.add(snapshotEvent)

        val viewportEvent = Event(
            type = EventType.CUSTOM,
            timestamp = captureEvent.timestamp,
            sid = nextSid(),
            data = EventDataUnion.CustomEventDataWrapper(
                buildJsonObject {
                    put("tag", RRWebCustomDataTag.VIEWPORT.wireValue)
                    putJsonObject("payload") {
                        put("width", captureEvent.origWidth)
                        put("height", captureEvent.origHeight)
                        put("availWidth", captureEvent.origWidth)
                        put("availHeight", captureEvent.origHeight)
                        put("colorDepth", 30)
                        put("pixelDepth", 30)
                        put("orientation", 0)
                    }
                }
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
                            buildJsonObject {
                                put("source", RRWebIncrementalSource.MOUSE_INTERACTION.code)
                                putJsonArray("texts") {}
                                put("type", RRWebMouseInteraction.TOUCH_START.code)
                                put("id", currentImageNodeId)
                                put("x", firstPosition.x + RRWEB_DOCUMENT_PADDING)
                                put("y", firstPosition.y + RRWEB_DOCUMENT_PADDING)
                            }
                        )
                    )
                )
                events.add(
                    Event(
                        type = EventType.CUSTOM,
                        timestamp = firstPosition.timestamp,
                        sid = nextSid(),
                        data = EventDataUnion.CustomEventDataWrapper(
                            buildJsonObject {
                                put("tag", RRWebCustomDataTag.CLICK.wireValue)
                                putJsonObject("payload") {
                                    put("clickTarget", CLICK_TARGET_VALUE)
                                    put("clickTextContent", CLICK_TEXT_CONTENT_VALUE)
                                    put("clickSelector", CLICK_SELECTOR_VALUE)
                                }
                            }
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
                            buildJsonObject {
                                put("source", RRWebIncrementalSource.MOUSE_INTERACTION.code)
                                putJsonArray("texts") {}
                                put("type", RRWebMouseInteraction.TOUCH_END.code)
                                put("id", currentImageNodeId)
                                put("x", lastPosition.x + RRWEB_DOCUMENT_PADDING)
                                put("y", lastPosition.y + RRWEB_DOCUMENT_PADDING)
                            }
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
                                buildJsonObject {
                                    put("source", RRWebIncrementalSource.TOUCH_MOVE.code)
                                    put("positions", buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", currentImageNodeId)
                                                put("timeOffset", 0)
                                                put("x", position.x + RRWEB_DOCUMENT_PADDING)
                                                put("y", position.y + RRWEB_DOCUMENT_PADDING)
                                            }
                                        )
                                    })
                                }
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
            put("tag", JsonPrimitive(RRWebCustomDataTag.IDENTIFY.wireValue))
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
