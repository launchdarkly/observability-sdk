package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.EventData
import com.launchdarkly.observability.replay.EventDataUnion
import com.launchdarkly.observability.replay.EventNode
import com.launchdarkly.observability.replay.EventType
import com.launchdarkly.observability.replay.RRWebCustomDataTag
import com.launchdarkly.observability.replay.capture.ExportFrame
import com.launchdarkly.observability.replay.capture.ImageSignature
import com.launchdarkly.observability.replay.capture.IntRect
import com.launchdarkly.observability.replay.capture.IntSize
import com.launchdarkly.observability.replay.capture.TileSignature
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val obj = custom.data.jsonObject
        assertEquals("Track", obj["tag"]!!.jsonPrimitive.content)
        // payload is a stringified JSON, matching the web `addCustomEvent('Track', stringify(...))`
        val payloadJson = Json.parseToJsonElement(obj["payload"]!!.jsonPrimitive.content).jsonObject
        assertEquals("purchase", payloadJson["event"]!!.jsonPrimitive.content)
        assertEquals(9.99, payloadJson["value"]!!.jsonPrimitive.double)
        val data = payloadJson["data"]!!.jsonObject
        assertEquals("USD", data["currency"]!!.jsonPrimitive.content)
        assertEquals("2", data["count"]!!.jsonPrimitive.content)
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
        val payloadJson = Json.parseToJsonElement(custom.data.jsonObject["payload"]!!.jsonPrimitive.content).jsonObject
        assertEquals("login", payloadJson["event"]!!.jsonPrimitive.content)
        assertFalse(payloadJson.containsKey("value"))
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
        val obj = custom.data.jsonObject
        assertEquals("Navigate", obj["tag"]!!.jsonPrimitive.content)
        // payload is the route as a plain string, matching web `addCustomEvent('Navigate', url)`
        assertEquals("Profile", obj["payload"]!!.jsonPrimitive.content)
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
        val obj = custom.data.jsonObject
        assertEquals("Foreground", obj["tag"]!!.jsonPrimitive.content)
        // payload is a stringified JSON object, matching the web/Swift rrweb Custom event contract.
        val payloadJson = Json.parseToJsonElement(obj["payload"]!!.jsonPrimitive.content).jsonObject
        assertEquals("foreground", payloadJson["lifecycle_state"]!!.jsonPrimitive.content)
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
        val obj = custom.data.jsonObject
        assertEquals("Background", obj["tag"]!!.jsonPrimitive.content)
        val payloadJson = Json.parseToJsonElement(obj["payload"]!!.jsonPrimitive.content).jsonObject
        assertEquals("background", payloadJson["lifecycle_state"]!!.jsonPrimitive.content)
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
        scale = 1f,
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
