package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.EventData
import com.launchdarkly.observability.replay.EventDataUnion
import com.launchdarkly.observability.replay.EventType
import com.launchdarkly.observability.replay.capture.ExportFrame
import com.launchdarkly.observability.replay.capture.IntRect
import com.launchdarkly.observability.replay.capture.IntSize
import com.launchdarkly.observability.replay.capture.TileSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RRWebEventGeneratorTest {

    @Test
    fun `keyframe incremental resolves removes before map reset`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1)
        val tileA = TileSignature(101)
        val tileB = TileSignature(202)

        generator.generateCaptureFullEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = true,
                addImages = listOf(addImage(tileA, 0, 0, 120, 88)),
                removeImages = null,
                timestamp = 1L,
            )
        )

        val bEvents = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = false,
                addImages = listOf(addImage(tileB, 0, 0, 120, 22)),
                removeImages = null,
                timestamp = 2L,
            )
        )
        val bAddId = mutationData(bEvents).adds!!.single().node.id!!

        val rollbackEvents = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 2,
                isKeyframe = true,
                addImages = listOf(addImage(tileA, 0, 0, 120, 88)),
                removeImages = listOf(ExportFrame.RemoveImage(keyFrameId = 1, tileSignature = tileB)),
                timestamp = 3L,
            )
        )

        val rollbackRemoves = mutationData(rollbackEvents).removes!!
        assertEquals(setOf(bAddId), rollbackRemoves.map { it.id }.toSet())
    }

    @Test
    fun `backtracking supports two remove-only rollbacks`() {
        val generator = RRWebEventGenerator(canvasDrawEntourage = 1)
        val tileA = TileSignature(101)
        val tileB = TileSignature(202)
        val tileC = TileSignature(303)

        generator.generateCaptureFullEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = true,
                addImages = listOf(addImage(tileA, 0, 0, 120, 88)),
                removeImages = null,
                timestamp = 1L,
            )
        )

        val bEvents = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = false,
                addImages = listOf(addImage(tileB, 0, 0, 120, 22)),
                removeImages = null,
                timestamp = 2L,
            )
        )
        val bAddId = mutationData(bEvents).adds!!.single().node.id!!

        val cEvents = generator.generateCaptureIncrementalEvents(
            exportFrame(
                keyFrameId = 1,
                isKeyframe = false,
                addImages = listOf(addImage(tileC, 0, 66, 120, 22)),
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
                removeImages = listOf(ExportFrame.RemoveImage(keyFrameId = 1, tileSignature = tileC)),
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
                removeImages = listOf(ExportFrame.RemoveImage(keyFrameId = 1, tileSignature = tileB)),
                timestamp = 5L,
            )
        )
        val rollbackToAData = mutationData(rollbackToA)
        assertTrue(rollbackToAData.adds.isNullOrEmpty())
        assertEquals(setOf(bAddId), rollbackToAData.removes!!.map { it.id }.toSet())
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
        format = ExportFrame.ExportFormat.Webp(quality = 30),
        timestamp = timestamp,
        orientation = 0,
        isKeyframe = isKeyframe,
        imageSignature = null,
        session = "session",
    )

    private fun addImage(
        tileSignature: TileSignature,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): ExportFrame.AddImage = ExportFrame.AddImage(
        imageBase64 = "AQ==",
        rect = IntRect(left = left, top = top, width = width, height = height),
        tileSignature = tileSignature,
    )

    private fun mutationData(events: List<Event>): EventData {
        val event = events.first { it.type == EventType.INCREMENTAL_SNAPSHOT }
        val data = event.data as EventDataUnion.StandardEventData
        return data.data
    }
}
