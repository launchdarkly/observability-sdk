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
