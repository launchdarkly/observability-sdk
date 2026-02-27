package com.launchdarkly.observability.replay.exporter

import android.graphics.Bitmap
import android.util.Base64
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.EventData
import com.launchdarkly.observability.replay.EventDataUnion
import com.launchdarkly.observability.replay.EventNode
import com.launchdarkly.observability.replay.EventType
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.capture.ExportDiffManager
import com.launchdarkly.observability.replay.capture.ImageCaptureService
import com.launchdarkly.observability.replay.capture.ImageSignature
import com.launchdarkly.observability.replay.capture.IntRect
import com.launchdarkly.observability.replay.capture.IntSize
import com.launchdarkly.observability.replay.capture.TileDiffManager
import com.launchdarkly.observability.replay.capture.TileSignature
import com.launchdarkly.observability.replay.capture.TiledFrame
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.Base64 as JBase64

class RawFramesRRWebEventGeneratorTest {
    private val screenWidth = 120
    private val screenHeight = 88

    @Test
    fun `converts three raw frames into expected colored images`() {
        mockBase64Android()
        try {
            val method = ReplayOptions.CompressionMethod.OverlayTiles(layers = 15, backtracking = false)
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(compression = method, scale = 1f, tileDiffManager = tileDiffManager)
            val eventGenerator = RRWebEventGenerator(canvasDrawEntourage = 300)

            val sigRed = imageSignature(11)
            val sigGreen = imageSignature(22)
            val sigBlue = imageSignature(33)

            val redTile = markerBitmapTile(marker = 0x11, width = screenWidth, height = screenHeight)
            val greenTile = markerBitmapTile(marker = 0x22, width = screenWidth, height = screenHeight)
            val blueTile = markerBitmapTile(marker = 0x33, width = screenWidth, height = screenHeight)

            val frame1 = rawFrame(1L)
            val frame2 = rawFrame(2L)
            val frame3 = rawFrame(3L)

            every { tileDiffManager.computeTiledFrame(frame1) } returns tiledFrame(
                id = 1, timestamp = 1L, isKeyframe = true, signature = sigRed, tiles = listOf(redTile)
            )
            every { tileDiffManager.computeTiledFrame(frame2) } returns tiledFrame(
                id = 2, timestamp = 2L, isKeyframe = true, signature = sigGreen, tiles = listOf(greenTile)
            )
            every { tileDiffManager.computeTiledFrame(frame3) } returns tiledFrame(
                id = 3, timestamp = 3L, isKeyframe = true, signature = sigBlue, tiles = listOf(blueTile)
            )

            val export1 = exportDiffManager.createCaptureEvent(frame1, "s")!!
            val export2 = exportDiffManager.createCaptureEvent(frame2, "s")!!
            val export3 = exportDiffManager.createCaptureEvent(frame3, "s")!!

            val events1 = eventGenerator.generateCaptureFullEvents(export1)
            val events2 = eventGenerator.generateCaptureIncrementalEvents(export2)
            val events3 = eventGenerator.generateCaptureIncrementalEvents(export3)

            val markers = extractEventImageMarkers(events1) + extractEventImageMarkers(events2) + extractEventImageMarkers(events3)
            val sizes = extractEventImageSizes(events1) + extractEventImageSizes(events2) + extractEventImageSizes(events3)

            assertEquals(listOf(0x11, 0x22, 0x33), markers)
            assertEquals(3, sizes.size)
            assertTrue(sizes.all { it == IntSize(screenWidth, screenHeight) })
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    @Test
    fun `backtracking emits smaller add and remove-only rollback`() {
        mockBase64Android()
        try {
            val method = ReplayOptions.CompressionMethod.OverlayTiles(layers = 15, backtracking = true)
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(compression = method, scale = 1f, tileDiffManager = tileDiffManager)
            val eventGenerator = RRWebEventGenerator(canvasDrawEntourage = 300)

            val sigBase = imageSignature(101)
            val sigBar = imageSignature(202)

            val baseTile = markerBitmapTile(marker = 0x55, width = screenWidth, height = screenHeight)
            val topBarTile = markerBitmapTile(marker = 0x66, width = screenWidth, height = 22, top = 0)

            val frame1 = rawFrame(1L)
            val frame2 = rawFrame(2L)
            val frame3 = rawFrame(3L)

            every { tileDiffManager.computeTiledFrame(frame1) } returns tiledFrame(
                id = 1, timestamp = 1L, isKeyframe = true, signature = sigBase, tiles = listOf(baseTile)
            )
            every { tileDiffManager.computeTiledFrame(frame2) } returns tiledFrame(
                id = 2, timestamp = 2L, isKeyframe = false, signature = sigBar, tiles = listOf(topBarTile)
            )
            every { tileDiffManager.computeTiledFrame(frame3) } returns tiledFrame(
                id = 3, timestamp = 3L, isKeyframe = false, signature = sigBase, tiles = listOf(baseTile)
            )

            val export1 = exportDiffManager.createCaptureEvent(frame1, "s")!!
            val export2 = exportDiffManager.createCaptureEvent(frame2, "s")!!
            val export3 = exportDiffManager.createCaptureEvent(frame3, "s")!!

            val events1 = eventGenerator.generateCaptureFullEvents(export1)
            val events2 = eventGenerator.generateCaptureIncrementalEvents(export2)
            val events3 = eventGenerator.generateCaptureIncrementalEvents(export3)

            val firstSize = firstAddedImageSize(events1)
            val secondSize = firstAddedImageSize(events2)
            assertNotNull(firstSize)
            assertNotNull(secondSize)
            assertEquals(IntSize(screenWidth, screenHeight), firstSize)
            assertEquals(screenWidth, secondSize!!.width)
            assertTrue(secondSize.height < firstSize!!.height)

            val thirdMutation = firstMutationData(events3)
            assertNotNull(thirdMutation)
            assertTrue(thirdMutation!!.adds.isNullOrEmpty())
            assertFalse(thirdMutation.removes.isNullOrEmpty())
            val secondAddedIds = addedNodeIds(events2).toSet()
            val thirdRemovedIds = thirdMutation.removes!!.map { it.id }.toSet()
            assertFalse(secondAddedIds.isEmpty())
            assertEquals(secondAddedIds, thirdRemovedIds)
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    @Test
    fun `backtracking across top and bottom bars supports two rollbacks`() {
        mockBase64Android()
        try {
            val method = ReplayOptions.CompressionMethod.OverlayTiles(layers = 15, backtracking = true)
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(compression = method, scale = 1f, tileDiffManager = tileDiffManager)
            val eventGenerator = RRWebEventGenerator(canvasDrawEntourage = 300)

            val sigA = imageSignature(301)
            val sigB = imageSignature(302)
            val sigC = imageSignature(303)

            val fullBlue = markerBitmapTile(marker = 0x71, width = screenWidth, height = screenHeight)
            val topGreen = markerBitmapTile(marker = 0x72, width = screenWidth, height = 22, top = 0)
            val bottomGreen = markerBitmapTile(marker = 0x73, width = screenWidth, height = 22, top = screenHeight - 22)

            val frame1 = rawFrame(1L)
            val frame2 = rawFrame(2L)
            val frame3 = rawFrame(3L)
            val frame4 = rawFrame(4L)
            val frame5 = rawFrame(5L)

            every { tileDiffManager.computeTiledFrame(frame1) } returns tiledFrame(
                id = 1, timestamp = 1L, isKeyframe = true, signature = sigA, tiles = listOf(fullBlue)
            )
            every { tileDiffManager.computeTiledFrame(frame2) } returns tiledFrame(
                id = 2, timestamp = 2L, isKeyframe = false, signature = sigB, tiles = listOf(topGreen)
            )
            every { tileDiffManager.computeTiledFrame(frame3) } returns tiledFrame(
                id = 3, timestamp = 3L, isKeyframe = false, signature = sigC, tiles = listOf(bottomGreen)
            )
            every { tileDiffManager.computeTiledFrame(frame4) } returns tiledFrame(
                id = 4, timestamp = 4L, isKeyframe = false, signature = sigB, tiles = listOf(topGreen)
            )
            every { tileDiffManager.computeTiledFrame(frame5) } returns tiledFrame(
                id = 5, timestamp = 5L, isKeyframe = false, signature = sigA, tiles = listOf(fullBlue)
            )

            val export1 = exportDiffManager.createCaptureEvent(frame1, "s")!!
            val export2 = exportDiffManager.createCaptureEvent(frame2, "s")!!
            val export3 = exportDiffManager.createCaptureEvent(frame3, "s")!!
            val export4 = exportDiffManager.createCaptureEvent(frame4, "s")!!
            val export5 = exportDiffManager.createCaptureEvent(frame5, "s")!!

            val events1 = eventGenerator.generateCaptureFullEvents(export1)
            val events2 = eventGenerator.generateCaptureIncrementalEvents(export2)
            val events3 = eventGenerator.generateCaptureIncrementalEvents(export3)
            val events4 = eventGenerator.generateCaptureIncrementalEvents(export4)
            val events5 = eventGenerator.generateCaptureIncrementalEvents(export5)

            val firstSize = firstAddedImageSize(events1)
            val secondSize = firstAddedImageSize(events2)
            val thirdSize = firstAddedImageSize(events3)
            assertEquals(IntSize(screenWidth, screenHeight), firstSize)
            assertEquals(screenWidth, secondSize!!.width)
            assertTrue(secondSize.height < firstSize!!.height)
            assertEquals(screenWidth, thirdSize!!.width)
            assertTrue(thirdSize.height < firstSize.height)

            val fourthMutation = firstMutationData(events4)!!
            assertTrue(fourthMutation.adds.isNullOrEmpty())
            assertFalse(fourthMutation.removes.isNullOrEmpty())
            val thirdAddedIds = addedNodeIds(events3).toSet()
            val fourthRemovedIds = fourthMutation.removes!!.map { it.id }.toSet()
            assertEquals(thirdAddedIds, fourthRemovedIds)

            val fifthMutation = firstMutationData(events5)!!
            assertTrue(fifthMutation.adds.isNullOrEmpty())
            assertFalse(fifthMutation.removes.isNullOrEmpty())
            val secondAddedIds = addedNodeIds(events2).toSet()
            val fifthRemovedIds = fifthMutation.removes!!.map { it.id }.toSet()
            assertEquals(secondAddedIds, fifthRemovedIds)
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    @Test
    fun `keyframe resets backtracking when layer limit reached`() {
        mockBase64Android()
        try {
            val method = ReplayOptions.CompressionMethod.OverlayTiles(layers = 3, backtracking = true)
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(compression = method, scale = 1f, tileDiffManager = tileDiffManager)
            val eventGenerator = RRWebEventGenerator(canvasDrawEntourage = 300)

            val sigA = imageSignature(801)
            val sigB = imageSignature(802)
            val sigC = imageSignature(803)

            val fullBlue = markerBitmapTile(marker = 0x41, width = screenWidth, height = screenHeight)
            val topGreen = markerBitmapTile(marker = 0x42, width = screenWidth, height = 22, top = 0)
            val bottomGreen = markerBitmapTile(marker = 0x43, width = screenWidth, height = 22, top = screenHeight - 22)

            val frame1 = rawFrame(1L)
            val frame2 = rawFrame(2L)
            val frame3 = rawFrame(3L)
            val frame4 = rawFrame(4L)
            val frame5 = rawFrame(5L)

            every { tileDiffManager.computeTiledFrame(frame1) } returns tiledFrame(
                id = 1, timestamp = 1L, isKeyframe = true, signature = sigA, tiles = listOf(fullBlue)
            )
            every { tileDiffManager.computeTiledFrame(frame2) } returns tiledFrame(
                id = 2, timestamp = 2L, isKeyframe = false, signature = sigB, tiles = listOf(topGreen)
            )
            every { tileDiffManager.computeTiledFrame(frame3) } returns tiledFrame(
                id = 3, timestamp = 3L, isKeyframe = false, signature = sigC, tiles = listOf(bottomGreen)
            )
            // Layer-limit rollover: same visual as frame2 but forced keyframe.
            every { tileDiffManager.computeTiledFrame(frame4) } returns tiledFrame(
                id = 4, timestamp = 4L, isKeyframe = true, signature = sigB, tiles = listOf(topGreen)
            )
            // Frame 5 mirrors frame1 content.
            every { tileDiffManager.computeTiledFrame(frame5) } returns tiledFrame(
                id = 5, timestamp = 5L, isKeyframe = false, signature = sigA, tiles = listOf(fullBlue)
            )

            val export1 = exportDiffManager.createCaptureEvent(frame1, "s")!!
            val export2 = exportDiffManager.createCaptureEvent(frame2, "s")!!
            val export3 = exportDiffManager.createCaptureEvent(frame3, "s")!!
            val export4 = exportDiffManager.createCaptureEvent(frame4, "s")!!
            val export5 = exportDiffManager.createCaptureEvent(frame5, "s")!!

            val events1 = eventGenerator.generateCaptureFullEvents(export1)
            val events2 = eventGenerator.generateCaptureIncrementalEvents(export2)
            val events3 = eventGenerator.generateCaptureIncrementalEvents(export3)
            val events4 = eventGenerator.generateCaptureIncrementalEvents(export4)
            val events5 = eventGenerator.generateCaptureIncrementalEvents(export5)

            val trackedNodeIds = mutableSetOf<Int>()
            trackedNodeIds += addedNodeIds(events1)
            trackedNodeIds += addedNodeIds(events2)
            trackedNodeIds += addedNodeIds(events3)
            assertEquals(3, trackedNodeIds.size)
            assertTrue(export4.isKeyframe)

            val fourthMutation = firstMutationData(events4)
            assertNotNull(fourthMutation)
            assertEquals(1, fourthMutation!!.adds?.size ?: 0)
            assertEquals(3, fourthMutation.removes?.size ?: 0)
            val fourthRemovedIds = fourthMutation.removes!!.map { it.id }.toSet()
            assertEquals(trackedNodeIds, fourthRemovedIds)

            trackedNodeIds.removeAll(fourthRemovedIds)
            trackedNodeIds += fourthMutation.adds!!.mapNotNull { it.node.id }
            assertEquals(1, trackedNodeIds.size)

            val fifthMutation = firstMutationData(events5)
            assertNotNull(fifthMutation)
            assertFalse(fifthMutation!!.adds.isNullOrEmpty())
            assertTrue(fifthMutation.removes.isNullOrEmpty())

            trackedNodeIds += fifthMutation.adds!!.mapNotNull { it.node.id }
            assertEquals(2, trackedNodeIds.size)
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    @Test
    fun `multi-tile keyframe removes resolve across ExportDiffManager and RRWebEventGenerator`() {
        mockBase64Android()
        try {
            val method = ReplayOptions.CompressionMethod.OverlayTiles(layers = 3, backtracking = true)
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(compression = method, scale = 1f, tileDiffManager = tileDiffManager)
            val eventGenerator = RRWebEventGenerator(canvasDrawEntourage = 300)

            val sigA = ImageSignature(
                rows = 1, columns = 2, tileWidth = 60, tileHeight = 88,
                tileSignatures = listOf(TileSignature(101), TileSignature(102)),
            )
            val sigB = ImageSignature(
                rows = 1, columns = 1, tileWidth = 120, tileHeight = 22,
                tileSignatures = listOf(TileSignature(201)),
            )

            val frame1 = rawFrame(1L)
            val frame2 = rawFrame(2L)
            val frame3 = rawFrame(3L)

            every { tileDiffManager.computeTiledFrame(frame1) } returns tiledFrame(
                id = 1, timestamp = 1L, isKeyframe = true, signature = sigA,
                tiles = listOf(
                    markerBitmapTile(marker = 0x01, width = 60, height = screenHeight),
                    markerBitmapTile(marker = 0x02, width = 60, height = screenHeight),
                ),
            )
            every { tileDiffManager.computeTiledFrame(frame2) } returns tiledFrame(
                id = 2, timestamp = 2L, isKeyframe = false, signature = sigB,
                tiles = listOf(markerBitmapTile(marker = 0x03, width = screenWidth, height = 22)),
            )
            every { tileDiffManager.computeTiledFrame(frame3) } returns tiledFrame(
                id = 3, timestamp = 3L, isKeyframe = true, signature = sigA,
                tiles = listOf(
                    markerBitmapTile(marker = 0x04, width = 60, height = screenHeight),
                    markerBitmapTile(marker = 0x05, width = 60, height = screenHeight),
                ),
            )

            val export1 = exportDiffManager.createCaptureEvent(frame1, "s")!!
            val export2 = exportDiffManager.createCaptureEvent(frame2, "s")!!
            val export3 = exportDiffManager.createCaptureEvent(frame3, "s")!!

            val events1 = eventGenerator.generateCaptureFullEvents(export1)
            val events2 = eventGenerator.generateCaptureIncrementalEvents(export2)
            val events3 = eventGenerator.generateCaptureIncrementalEvents(export3)

            val idsAfterFrame1 = addedNodeIds(events1).toSet()
            val idsAfterFrame2 = addedNodeIds(events2).toSet()
            assertEquals(2, idsAfterFrame1.size, "Frame 1 has 2 tiles so 2 DOM nodes")
            assertEquals(1, idsAfterFrame2.size, "Frame 2 has 1 tile so 1 DOM node")

            val thirdMutation = firstMutationData(events3)
            assertNotNull(thirdMutation)
            assertFalse(
                thirdMutation!!.removes.isNullOrEmpty(),
                "Keyframe must produce removes for previous frames' nodes — " +
                    "fails if AddImage/RemoveImage use per-tile signatures that don't match nodeIds keys",
            )
            val removedIds = thirdMutation.removes!!.map { it.id }.toSet()
            val allPreviousIds = idsAfterFrame1 + idsAfterFrame2
            assertTrue(
                removedIds.all { it in allPreviousIds },
                "Every removed node id must be one that was previously added",
            )
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    private fun mockBase64Android() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), Base64.NO_WRAP) } answers {
            JBase64.getEncoder().encodeToString(firstArg())
        }
    }

    private fun rawFrame(timestamp: Long): ImageCaptureService.RawFrame {
        val bitmap = mockk<Bitmap>()
        every { bitmap.isRecycled } returns false
        every { bitmap.recycle() } just runs
        return ImageCaptureService.RawFrame(bitmap = bitmap, timestamp = timestamp, orientation = 0)
    }

    private fun imageSignature(seed: Long): ImageSignature = ImageSignature(
        rows = 1,
        columns = 1,
        tileWidth = 64,
        tileHeight = 22,
        tileSignatures = listOf(TileSignature(seed)),
    )

    private fun tiledFrame(
        id: Int,
        timestamp: Long,
        isKeyframe: Boolean,
        signature: ImageSignature,
        tiles: List<TiledFrame.Tile>,
    ): TiledFrame = TiledFrame(
        id = id,
        tiles = tiles,
        scale = 1f,
        originalSize = IntSize(screenWidth, screenHeight),
        timestamp = timestamp,
        orientation = 0,
        isKeyframe = isKeyframe,
        imageSignature = signature,
    )

    private fun markerBitmapTile(marker: Int, width: Int, height: Int, top: Int = 0): TiledFrame.Tile {
        val bitmap = mockk<Bitmap>()
        every { bitmap.isRecycled } returns false
        every { bitmap.recycle() } just runs
        every { bitmap.compress(any(), any(), any()) } answers {
            val stream = thirdArg<ByteArrayOutputStream>()
            stream.write(byteArrayOf(marker.toByte()))
            true
        }
        return TiledFrame.Tile(
            bitmap = bitmap,
            rect = IntRect(left = 0, top = top, width = width, height = height),
        )
    }

    private fun extractEventImageMarkers(events: List<Event>): List<Int> {
        val markers = mutableListOf<Int>()
        for (event in events) {
            val data = (event.data as? EventDataUnion.StandardEventData)?.data ?: continue
            data.node?.let { markers += markersFromNodes(it.childNodes) }
            data.adds?.forEach { add ->
                markerFromDataURL(add.node.attributes?.get("src"))?.let { markers += it }
            }
        }
        return markers
    }

    private fun extractEventImageSizes(events: List<Event>): List<IntSize> {
        val sizes = mutableListOf<IntSize>()
        for (event in events) {
            val data = (event.data as? EventDataUnion.StandardEventData)?.data ?: continue
            data.node?.let { sizes += sizesFromNodes(it.childNodes) }
            data.adds?.forEach { add ->
                sizeFromNode(add.node)?.let { sizes += it }
            }
        }
        return sizes
    }

    private fun firstAddedImageSize(events: List<Event>): IntSize? {
        for (event in events) {
            val data = (event.data as? EventDataUnion.StandardEventData)?.data ?: continue
            data.node?.let { node ->
                sizesFromNodes(node.childNodes).firstOrNull()?.let { return it }
            }
            data.adds?.firstNotNullOfOrNull { sizeFromNode(it.node) }?.let { return it }
        }
        return null
    }

    private fun firstMutationData(events: List<Event>): EventData? =
        events.firstOrNull { it.type == EventType.INCREMENTAL_SNAPSHOT }
            ?.data
            ?.let { it as? EventDataUnion.StandardEventData }
            ?.data

    private fun addedNodeIds(events: List<Event>): List<Int> {
        val ids = mutableListOf<Int>()
        for (event in events) {
            val data = (event.data as? EventDataUnion.StandardEventData)?.data ?: continue
            data.node?.let { ids += imageNodeIdsFromNodes(it.childNodes) }
            data.adds?.forEach { add -> add.node.id?.let { ids += it } }
        }
        return ids
    }

    private fun markersFromNodes(nodes: List<EventNode>): List<Int> {
        val markers = mutableListOf<Int>()
        for (node in nodes) {
            if (node.tagName == "img") {
                markerFromDataURL(node.attributes?.get("src"))?.let { markers += it }
            }
            markers += markersFromNodes(node.childNodes)
        }
        return markers
    }

    private fun imageNodeIdsFromNodes(nodes: List<EventNode>): List<Int> {
        val ids = mutableListOf<Int>()
        for (node in nodes) {
            node.id?.let { if (node.tagName == "img") ids += it }
            ids += imageNodeIdsFromNodes(node.childNodes)
        }
        return ids
    }

    private fun sizesFromNodes(nodes: List<EventNode>): List<IntSize> {
        val sizes = mutableListOf<IntSize>()
        for (node in nodes) {
            sizeFromNode(node)?.let { sizes += it }
            sizes += sizesFromNodes(node.childNodes)
        }
        return sizes
    }

    private fun sizeFromNode(node: EventNode): IntSize? {
        if (node.tagName != "img") return null
        val attrs = node.attributes ?: return null
        val width = attrs["width"]?.toIntOrNull() ?: return null
        val height = attrs["height"]?.toIntOrNull() ?: return null
        return IntSize(width, height)
    }

    private fun markerFromDataURL(dataURL: String?): Int? {
        val input = dataURL ?: return null
        val comma = input.indexOf(',')
        if (comma <= 0) return null
        val bytes = JBase64.getDecoder().decode(input.substring(comma + 1))
        return bytes.firstOrNull()?.toInt()?.and(0xFF)
    }
}
