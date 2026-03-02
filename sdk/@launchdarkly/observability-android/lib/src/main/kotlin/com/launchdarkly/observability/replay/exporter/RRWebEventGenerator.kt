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
import com.launchdarkly.observability.replay.capture.ExportFrame
import com.launchdarkly.observability.replay.capture.ImageSignature
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
        private const val RRWEB_INITIAL_NODE_ID = 16
        private const val DOM_HTML = "html"
        private const val DOM_HEAD = "head"
        private const val DOM_BODY = "body"
        private const val DOM_LANG = "lang"
        private const val DOM_LANG_EN = "en"
        private const val DOM_STYLE = "style"
        private const val DOM_BODY_STYLE = "position:relative;"
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
    private var lastNodeId: Int = RRWEB_INITIAL_NODE_ID
    private var imageNodeId: Int? = null
    private var bodyNodeId: Int? = null
    private var knownKeyFrameId: Int? = null
    private val nodeIds = mutableMapOf<ImageSignature, Int>()

    data class State(
        val lastSid: Int = 0,
        val generatingCanvasSize: Int = 0,
        val lastNodeId: Int = RRWEB_INITIAL_NODE_ID,
        val imageNodeId: Int? = null,
        val bodyNodeId: Int? = null,
        val knownKeyFrameId: Int? = null,
        val nodeIds: Map<ImageSignature, Int> = emptyMap(),
    )

    private fun nextSid(): Int {
        lastSid++
        return lastSid
    }

    private fun nextNodeId(): Int {
        lastNodeId++
        return lastNodeId
    }

    private fun imageMimeType(format: ExportFrame.ExportFormat): String =
        when (format) {
            ExportFrame.ExportFormat.Png -> "image/png"
            is ExportFrame.ExportFormat.Jpeg -> "image/jpeg"
            is ExportFrame.ExportFormat.Webp -> "image/webp"
        }

    private fun tileNode(exportFrame: ExportFrame, image: ExportFrame.AddImage): Pair<EventNode, Int> {
        val tileCanvasId = nextNodeId()
        image.imageSignature?.let { nodeIds[it] = tileCanvasId }
        val dataUrl = "data:${imageMimeType(exportFrame.format)};base64,${image.imageBase64}"
        val node = EventNode(
            id = tileCanvasId,
            type = NodeType.ELEMENT,
            tagName = "img",
            attributes = mapOf(
                "src" to dataUrl,
                "width" to "${image.rect.width}",
                "height" to "${image.rect.height}",
                "style" to "position:absolute;left:${image.rect.left}px;top:${image.rect.top}px;pointer-events:none;",
            ),
            childNodes = emptyList(),
        )
        return node to dataUrl.length
    }

    private fun addCommandNodes(exportFrame: ExportFrame): List<Event> {
        val bodyId = bodyNodeId ?: return emptyList()

        var totalCanvasSize = 0
        val removes = exportFrame.removeImages?.mapNotNull { removal ->
            nodeIds[removal.imageSignature]?.let { nodeId ->
                Removal(parentId = bodyId, id = nodeId)
            }
        } ?: emptyList()

        if (exportFrame.isKeyframe) {
            nodeIds.clear()
        } else if (exportFrame.keyFrameId != knownKeyFrameId) {
            // Drop frame, it cannot be reconstructed from currently known keyframe state.
            return emptyList()
        }

        val adds = exportFrame.addImages.map { image ->
            val (node, canvasSize) = tileNode(exportFrame, image)
            totalCanvasSize += canvasSize
            Addition(parentId = bodyId, nextId = null, node = node)
        }

        if (exportFrame.isKeyframe) {
            adds.firstOrNull()?.node?.id?.let { firstId ->
                if (firstId != imageNodeId) {
                    imageNodeId = firstId
                }
            }
        }

        val mutationEvent = Event(
            type = EventType.INCREMENTAL_SNAPSHOT,
            timestamp = exportFrame.timestamp,
            sid = nextSid(),
            data = EventDataUnion.StandardEventData(
                EventData(
                    source = IncrementalSource.MUTATION,
                    adds = adds,
                    removes = removes,
                )
            ),
        )
        accumulatedCanvasSize += totalCanvasSize + canvasDrawEntourage
        return listOf(mutationEvent)
    }

    fun getState(): State = State(
        lastSid = lastSid,
        generatingCanvasSize = accumulatedCanvasSize,
        lastNodeId = lastNodeId,
        imageNodeId = imageNodeId,
        bodyNodeId = bodyNodeId,
        knownKeyFrameId = knownKeyFrameId,
        nodeIds = nodeIds.toMap(),
    )

    fun restoreState(state: State) {
        lastSid = state.lastSid
        accumulatedCanvasSize = state.generatingCanvasSize
        lastNodeId = state.lastNodeId
        imageNodeId = state.imageNodeId
        bodyNodeId = state.bodyNodeId
        knownKeyFrameId = state.knownKeyFrameId
        nodeIds.clear()
        nodeIds.putAll(state.nodeIds)
    }

    /**
     * Generates events for an incremental capture. Used after [generateCaptureFullEvents] has already been called
     * for a previous capture in the same session.
     */
    fun generateCaptureIncrementalEvents(exportFrame: ExportFrame): List<Event> {
        if (exportFrame.isKeyframe) {
            knownKeyFrameId = exportFrame.keyFrameId
        }
        return addCommandNodes(exportFrame)
    }

    /**
     * Generates events for a full capture. May be invoked multiple times for a single session if a substantial
     * change occurs requiring a full capture to be sent.
     */
    fun generateCaptureFullEvents(exportFrame: ExportFrame): List<Event> {
        if (exportFrame.addImages.isEmpty()) return emptyList()
        val eventBatch = mutableListOf<Event>()
        knownKeyFrameId = exportFrame.keyFrameId

        val metaEvent = Event(
            type = EventType.META,
            timestamp = exportFrame.timestamp,
            sid = nextSid(),
            data = EventDataUnion.StandardEventData(
                EventData(
                    width = exportFrame.originalSize.width + RRWEB_DOCUMENT_PADDING * 2,
                    height = exportFrame.originalSize.height + RRWEB_DOCUMENT_PADDING * 2,
                    )
            ),
        )
        eventBatch.add(metaEvent)

        lastNodeId = 0
        var totalCanvasSize = 0
        val documentNodeId = nextNodeId()
        val headNodeId = nextNodeId()
        val currentBodyNodeId = nextNodeId()
        val tileNodes = exportFrame.addImages.map { image ->
            val (node, canvasSize) = tileNode(exportFrame, image)
            totalCanvasSize += canvasSize
            node
        }
        val htmlNodeId = nextNodeId()

        val snapshotEvent = Event(
            type = EventType.FULL_SNAPSHOT,
            timestamp = exportFrame.timestamp,
            sid = nextSid(),
            data = EventDataUnion.StandardEventData(
                EventData(
                    node = EventNode(
                        id = documentNodeId,
                        type = NodeType.DOCUMENT,
                        childNodes = listOf(
                            EventNode(
                                id = htmlNodeId,
                                type = NodeType.ELEMENT,
                                tagName = DOM_HTML,
                                attributes = mapOf(DOM_LANG to DOM_LANG_EN),
                                childNodes = listOf(
                                    EventNode(
                                        id = headNodeId,
                                        type = NodeType.ELEMENT,
                                        tagName = DOM_HEAD,
                                        attributes = emptyMap(),
                                    ),
                                    EventNode(
                                        id = currentBodyNodeId,
                                        type = NodeType.ELEMENT,
                                        tagName = DOM_BODY,
                                        attributes = mapOf(DOM_STYLE to DOM_BODY_STYLE),
                                        childNodes = tileNodes
                                    )
                                )
                            )
                        ),
                    ),
                )
            ),
        )

        imageNodeId = tileNodes.firstOrNull()?.id
        bodyNodeId = currentBodyNodeId
        accumulatedCanvasSize = totalCanvasSize + canvasDrawEntourage
        eventBatch.add(snapshotEvent)

        val viewportEvent = Event(
            type = EventType.CUSTOM,
            timestamp = exportFrame.timestamp,
            sid = nextSid(),
            data = EventDataUnion.CustomEventDataWrapper(
                buildJsonObject {
                    put("tag", RRWebCustomDataTag.VIEWPORT.wireValue)
                    putJsonObject("payload") {
                        put("width", exportFrame.originalSize.width)
                        put("height", exportFrame.originalSize.height)
                        put("availWidth", exportFrame.originalSize.width)
                        put("availHeight", exportFrame.originalSize.height)
                        put("colorDepth", 30)
                        put("pixelDepth", 30)
                        put("orientation", exportFrame.orientation)
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
                                imageNodeId?.let { put("id", it) }
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
                                imageNodeId?.let { put("id", it) }
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
                                                imageNodeId?.let { put("id", it) }
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
