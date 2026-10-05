package com.launchdarkly.observability.replay.exporter

import android.view.MotionEvent
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.EventData
import com.launchdarkly.observability.replay.EventDataUnion
import com.launchdarkly.observability.replay.EventNode
import com.launchdarkly.observability.replay.EventType
import com.launchdarkly.observability.replay.InteractionEvent
import com.launchdarkly.observability.replay.Position
import com.launchdarkly.observability.replay.RRWebCustomDataTag
import com.launchdarkly.observability.replay.RRWebMouseInteraction
import com.launchdarkly.observability.replay.capture.ExportFrame
import com.launchdarkly.observability.replay.capture.ImageSignature
import com.launchdarkly.observability.replay.capture.IntRect
import com.launchdarkly.observability.replay.capture.IntSize
import com.launchdarkly.observability.replay.capture.TileSignature
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RRWebEventGeneratorTest {
    @Test
    fun `convenience export frame uses jpeg mime type`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val exportFrame = ExportFrame("AQ==", 88, 120, 1L, "session")

        val events = generator.generateCaptureFullEvents(exportFrame)
        val src = firstImageSrc(events)

        assertTrue(src.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `keyframe incremental resolves removes before map reset`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val sigA = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(TileSignature(101)))
        val sigB = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(TileSignature(202)))

        generator.generateCaptureFullEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = true,
                addImages = listOf(addImage(sigA, 0, 0, 120, 88)),
                removeImages = null,
                timestamp = 1L,
            )
        )

        val bEvents = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = false,
                addImages = listOf(addImage(sigB, 0, 0, 120, 22)),
                removeImages = null,
                timestamp = 2L,
            )
        )
        val bAddId = mutationData(bEvents).adds!!.single().node.id!!

        val rollbackEvents = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 2,
                isKeyframe = true,
                addImages = listOf(addImage(sigA, 0, 0, 120, 88)),
                removeImages = listOf(ExportFrame.RemoveImage(keyFrameId = 1, imageSignature = sigB)),
                timestamp = 3L,
            )
        )

        val rollbackRemoves = mutationData(rollbackEvents).removes!!
        assertEquals(setOf(bAddId), rollbackRemoves.map { it.id }.toSet())
    }

    @Test
    fun `backtracking supports two remove-only rollbacks`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val sigA = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(TileSignature(101)))
        val sigB = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(TileSignature(202)))
        val sigC = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(TileSignature(303)))

        generator.generateCaptureFullEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = true,
                addImages = listOf(addImage(sigA, 0, 0, 120, 88)),
                removeImages = null,
                timestamp = 1L,
            )
        )

        val bEvents = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = false,
                addImages = listOf(addImage(sigB, 0, 0, 120, 22)),
                removeImages = null,
                timestamp = 2L,
            )
        )
        val bAddId = mutationData(bEvents).adds!!.single().node.id!!

        val cEvents = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = false,
                addImages = listOf(addImage(sigC, 0, 66, 120, 22)),
                removeImages = null,
                timestamp = 3L,
            )
        )
        val cAddId = mutationData(cEvents).adds!!.single().node.id!!

        val rollbackToB = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = false,
                addImages = emptyList(),
                removeImages = listOf(ExportFrame.RemoveImage(keyFrameId = 1, imageSignature = sigC)),
                timestamp = 4L,
            )
        )
        val rollbackToBData = mutationData(rollbackToB)
        assertTrue(rollbackToBData.adds.isNullOrEmpty())
        assertEquals(setOf(cAddId), rollbackToBData.removes!!.map { it.id }.toSet())

        val rollbackToA = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = false,
                addImages = emptyList(),
                removeImages = listOf(ExportFrame.RemoveImage(keyFrameId = 1, imageSignature = sigB)),
                timestamp = 5L,
            )
        )
        val rollbackToAData = mutationData(rollbackToA)
        assertTrue(rollbackToAData.adds.isNullOrEmpty())
        assertEquals(setOf(bAddId), rollbackToAData.removes!!.map { it.id }.toSet())
    }

    @Test
    fun `generateTrackEvent emits Track custom event with stringified payload`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val payload = TrackItemPayload(
            name = "purchase",
            metricValue = 9.99,
            attributes = mapOf("currency" to "USD", "count" to "2"),
            timestamp = 42L,
            sessionId = "session",
        )

        val event = generator.generateTrackEvent(payload)!!

        assertEquals(EventType.CUSTOM, event.type)
        val custom = event.data as EventDataUnion.CustomEventDataWrapper
        val obj = custom.data
        assertEquals("Track", obj["tag"])
        // payload is a stringified JSON, matching the web `addCustomEvent('Track', stringify(...))`
        val payloadJson = JSONObject(obj["payload"] as String)
        assertEquals("purchase", payloadJson.getString("event"))
        assertEquals(9.99, payloadJson.getDouble("value"))
        val data = payloadJson.getJSONObject("data")
        assertEquals("USD", data.getString("currency"))
        assertEquals("2", data.getString("count"))
    }

    @Test
    fun `generateTrackEvent omits value when null`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val payload = TrackItemPayload(
            name = "login",
            metricValue = null,
            attributes = emptyMap(),
            timestamp = 1L,
            sessionId = "session",
        )

        val event = generator.generateTrackEvent(payload)!!

        val custom = event.data as EventDataUnion.CustomEventDataWrapper
        val payloadJson = JSONObject(custom.data["payload"] as String)
        assertEquals("login", payloadJson.getString("event"))
        assertFalse(payloadJson.has("value"))
    }

    @Test
    fun `generateNavigateEvent emits Navigate custom event with screen name payload`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val payload = NavigateItemPayload(
            name = "Profile",
            timestamp = 7L,
            sessionId = "session",
        )

        val event = generator.generateNavigateEvent(payload)

        assertEquals(EventType.CUSTOM, event.type)
        assertEquals(7L, event.timestamp)
        val custom = event.data as EventDataUnion.CustomEventDataWrapper
        val obj = custom.data
        assertEquals("Navigate", obj["tag"])
        // payload is the route as a plain string, matching web `addCustomEvent('Navigate', url)`
        assertEquals("Profile", obj["payload"])
    }

    @Test
    fun `generateAppLifecycleEvent emits Foreground custom event with stringified payload`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val payload = AppLifecycleItemPayload(
            tag = RRWebCustomDataTag.APP_FOREGROUND,
            lifecycleState = "foreground",
            timestamp = 11L,
            sessionId = "session",
        )

        val event = generator.generateAppLifecycleEvent(payload)!!

        assertEquals(EventType.CUSTOM, event.type)
        assertEquals(11L, event.timestamp)
        val custom = event.data as EventDataUnion.CustomEventDataWrapper
        val obj = custom.data
        assertEquals("Foreground", obj["tag"])
        // payload is a stringified JSON object, matching the web/Swift rrweb Custom event contract.
        val payloadJson = JSONObject(obj["payload"] as String)
        assertEquals("foreground", payloadJson.getString("lifecycle_state"))
    }

    @Test
    fun `generateAppLifecycleEvent emits Background custom event`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val payload = AppLifecycleItemPayload(
            tag = RRWebCustomDataTag.APP_BACKGROUND,
            lifecycleState = "background",
            timestamp = 12L,
            sessionId = "session",
        )

        val event = generator.generateAppLifecycleEvent(payload)!!

        val custom = event.data as EventDataUnion.CustomEventDataWrapper
        val obj = custom.data
        assertEquals("Background", obj["tag"])
        val payloadJson = JSONObject(obj["payload"] as String)
        assertEquals("background", payloadJson.getString("lifecycle_state"))
    }

    @Test
    fun `a touch down still draws the pointer trail and no longer claims a click`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val interaction = InteractionEvent(
            action = MotionEvent.ACTION_DOWN,
            positions = listOf(Position(x = 10, y = 20, timestamp = 5L)),
            session = "session",
        )

        val events = generator.generateInteractionEvents(interaction)

        // The trail snapshot replay needs to draw the pointer is untouched.
        assertEquals(1, events.size)
        assertEquals(EventType.INCREMENTAL_SNAPSHOT, events.single().type)
        val data = (events.single().data as EventDataUnion.CustomEventDataWrapper).data
        assertEquals(
            RRWebMouseInteraction.TOUCH_START.code,
            data["type"],
        )
        // A touch-down is not a click: it may still become a drag or a long press, and this stream
        // cannot see clicks an embedder resolves in its own UI tree. Clicks come from the funnel.
        assertTrue(events.none { it.type == EventType.CUSTOM })
    }

    @Test
    fun `generateClickEvent emits Click custom event with target, text and selector`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val payload = ClickItemPayload(
            target = "ElevatedButton",
            text = "Pay",
            id = "checkout.pay",
            screenId = "cart-1",
            screenName = "Cart",
            timestamp = 21L,
            sessionId = "session",
        )

        val event = generator.generateClickEvent(payload)

        assertEquals(EventType.CUSTOM, event.type)
        // Stamped from the payload, not from arrival: an embedder's click crosses an
        // asynchronous bridge and must still order against the touch snapshots around it.
        assertEquals(21L, event.timestamp)
        val custom = event.data as EventDataUnion.CustomEventDataWrapper
        val obj = custom.data
        assertEquals("Click", obj["tag"])
        val clickPayload = obj["payload"] as Map<*, *>
        assertEquals("ElevatedButton", clickPayload["clickTarget"])
        assertEquals("Pay", clickPayload["clickTextContent"])
        // Prefers the stable id, mirroring the web `#id` selector.
        assertEquals("checkout.pay", clickPayload["clickSelector"])
        assertEquals("cart-1", clickPayload["screenId"])
        assertEquals("Cart", clickPayload["screenName"])
    }

    @Test
    fun `generateClickEvent falls back to the target when no id is known`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1, title = "test")
        val payload = ClickItemPayload(
            target = "InkWell",
            text = null,
            id = null,
            screenId = null,
            screenName = null,
            timestamp = 22L,
            sessionId = "session",
        )

        val event = generator.generateClickEvent(payload)

        val custom = event.data as EventDataUnion.CustomEventDataWrapper
        val clickPayload = custom.data["payload"] as Map<*, *>
        assertEquals("InkWell", clickPayload["clickSelector"])
        assertEquals("", clickPayload["clickTextContent"])
        // Screen fields are omitted rather than sent empty when the stack has no screen.
        assertFalse(clickPayload.containsKey("screenId"))
        assertFalse(clickPayload.containsKey("screenName"))
    }

    private fun exportFrame(
        keyFrameId: Int,
        isKeyframe: Boolean,
        addImages: List<ExportFrame.AddImage>,
        removeImages: List<ExportFrame.RemoveImage>?,
        timestamp: Long,
    ): ExportFrame = ExportFrame(
        keyFrameId = keyFrameId,
        addImages = addImages,
        removeImages = removeImages,
        originalSize = IntSize(width = 120, height = 88),
        scale = 1.0,
        timestamp = timestamp,
        orientation = 0,
        isKeyframe = isKeyframe,
        imageSignature = null,
        session = "session",
    )

    private fun addImage(
        imageSignature: ImageSignature,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): ExportFrame.AddImage = ExportFrame.AddImage(
        imageBase64 = "AQ==",
        rect = IntRect(left = left, top = top, width = width, height = height),
        imageSignature = imageSignature,
    )

    private fun mutationData(events: List<Event>): EventData {
        val event = events.first { it.type == EventType.INCREMENTAL_SNAPSHOT }
        val data = event.data as EventDataUnion.StandardEventData
        return data.data
    }

    private fun firstImageSrc(events: List<Event>): String {
        val fullSnapshot = events.first { it.type == EventType.FULL_SNAPSHOT }
        val data = (fullSnapshot.data as EventDataUnion.StandardEventData).data
        val root = data.node ?: error("FULL_SNAPSHOT should include a root node")
        val imageNode = firstNodeWithTag(root, "img") ?: error("FULL_SNAPSHOT should include an image node")
        return imageNode.attributes?.get("src") ?: error("Image node should include src")
    }

    private fun firstNodeWithTag(node: EventNode, tagName: String): EventNode? {
        if (node.tagName == tagName) {
            return node
        }
        for (child in node.childNodes) {
            val match = firstNodeWithTag(child, tagName)
            if (match != null) {
                return match
            }
        }
        return null
    }
}
