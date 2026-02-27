package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap
import android.util.Base64
import com.launchdarkly.observability.replay.ReplayOptions
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

class ExportDiffManagerTest {

    @Test
    fun `backtracking emits remove-only frame on rollback`() {
        mockBase64Android()
        try {
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(
                compression = ReplayOptions.CompressionMethod.OverlayTiles(backtracking = true),
                scale = 1f,
                tileDiffManager = tileDiffManager,
            )

            val imageA = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(TileSignature(101L)))
            val imageB = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(TileSignature(202L)))

            val bitmap1 = mockCompressibleBitmap(0x01)
            val bitmap2 = mockCompressibleBitmap(0x02)
            val bitmap3 = mockCompressibleBitmap(0x03)

            val frame1 = ImageCaptureService.RawFrame(bitmap = bitmap1, timestamp = 1L, orientation = 0)
            val frame2 = ImageCaptureService.RawFrame(bitmap = bitmap2, timestamp = 2L, orientation = 0)
            val frame3 = ImageCaptureService.RawFrame(bitmap = bitmap3, timestamp = 3L, orientation = 0)

            every { tileDiffManager.computeTiledFrame(frame1) } returns TiledFrame(
                id = 1,
                tiles = listOf(TiledFrame.Tile(bitmap = bitmap1, rect = IntRect(0, 0, 120, 88))),
                scale = 1f,
                originalSize = IntSize(120, 88),
                timestamp = 1L,
                orientation = 0,
                isKeyframe = true,
                imageSignature = imageA,
            )
            every { tileDiffManager.computeTiledFrame(frame2) } returns TiledFrame(
                id = 2,
                tiles = listOf(TiledFrame.Tile(bitmap = bitmap2, rect = IntRect(0, 0, 120, 22))),
                scale = 1f,
                originalSize = IntSize(120, 88),
                timestamp = 2L,
                orientation = 0,
                isKeyframe = false,
                imageSignature = imageB,
            )
            every { tileDiffManager.computeTiledFrame(frame3) } returns TiledFrame(
                id = 3,
                tiles = listOf(TiledFrame.Tile(bitmap = bitmap3, rect = IntRect(0, 0, 120, 88))),
                scale = 1f,
                originalSize = IntSize(120, 88),
                timestamp = 3L,
                orientation = 0,
                isKeyframe = false,
                imageSignature = imageA,
            )

            val export1 = exportDiffManager.createCaptureEvent(frame1, session = "s")!!
            val export2 = exportDiffManager.createCaptureEvent(frame2, session = "s")!!
            val export3 = exportDiffManager.createCaptureEvent(frame3, session = "s")!!

            assertTrue(export1.addImages.isNotEmpty())
            assertTrue(export2.addImages.isNotEmpty())

            assertTrue(export3.addImages.isEmpty())
            assertNotNull(export3.removeImages)
            assertEquals(1, export3.removeImages!!.size)
            assertEquals(imageB, export3.removeImages!!.first().imageSignature)
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    @Test
    fun `multi-tile adds and removes store whole ImageSignature not per-tile`() {
        mockBase64Android()
        try {
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(
                compression = ReplayOptions.CompressionMethod.OverlayTiles(backtracking = false),
                scale = 1f,
                tileDiffManager = tileDiffManager,
            )

            val imageA = ImageSignature(
                rows = 1, columns = 2, tileWidth = 60, tileHeight = 88,
                tileSignatures = listOf(TileSignature(101L), TileSignature(102L)),
            )
            val imageB = ImageSignature(
                rows = 1, columns = 2, tileWidth = 60, tileHeight = 88,
                tileSignatures = listOf(TileSignature(201L), TileSignature(202L)),
            )

            val frame1 = rawFrame(1L)
            val frame2 = rawFrame(2L)

            every { tileDiffManager.computeTiledFrame(frame1) } returns TiledFrame(
                id = 1,
                tiles = listOf(
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x01), rect = IntRect(0, 0, 60, 88)),
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x02), rect = IntRect(60, 0, 60, 88)),
                ),
                scale = 1f,
                originalSize = IntSize(120, 88),
                timestamp = 1L,
                orientation = 0,
                isKeyframe = true,
                imageSignature = imageA,
            )
            every { tileDiffManager.computeTiledFrame(frame2) } returns TiledFrame(
                id = 2,
                tiles = listOf(
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x03), rect = IntRect(0, 0, 60, 88)),
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x04), rect = IntRect(60, 0, 60, 88)),
                ),
                scale = 1f,
                originalSize = IntSize(120, 88),
                timestamp = 2L,
                orientation = 0,
                isKeyframe = true,
                imageSignature = imageB,
            )

            val export1 = exportDiffManager.createCaptureEvent(frame1, session = "s")!!
            val export2 = exportDiffManager.createCaptureEvent(frame2, session = "s")!!

            assertEquals(2, export1.addImages.size)
            assertTrue(
                export1.addImages.all { it.imageSignature == imageA },
                "Every AddImage tile must carry the frame-level ImageSignature, not a per-tile signature",
            )

            assertNotNull(export2.removeImages)
            assertEquals(2, export2.removeImages!!.size)
            assertTrue(
                export2.removeImages!!.all { it.imageSignature == imageA },
                "Every RemoveImage must carry the previous frame's ImageSignature, not per-tile signatures",
            )
            assertEquals(2, export2.addImages.size)
            assertTrue(export2.addImages.all { it.imageSignature == imageB })
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    @Test
    fun `multi-tile backtracking removes carry whole ImageSignature`() {
        mockBase64Android()
        try {
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(
                compression = ReplayOptions.CompressionMethod.OverlayTiles(backtracking = true),
                scale = 1f,
                tileDiffManager = tileDiffManager,
            )

            val imageA = ImageSignature(
                rows = 1, columns = 2, tileWidth = 60, tileHeight = 88,
                tileSignatures = listOf(TileSignature(101L), TileSignature(102L)),
            )
            val imageB = ImageSignature(
                rows = 1, columns = 1, tileWidth = 120, tileHeight = 22,
                tileSignatures = listOf(TileSignature(201L)),
            )

            val frame1 = rawFrame(1L)
            val frame2 = rawFrame(2L)
            val frame3 = rawFrame(3L)

            every { tileDiffManager.computeTiledFrame(frame1) } returns TiledFrame(
                id = 1,
                tiles = listOf(
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x01), rect = IntRect(0, 0, 60, 88)),
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x02), rect = IntRect(60, 0, 60, 88)),
                ),
                scale = 1f, originalSize = IntSize(120, 88),
                timestamp = 1L, orientation = 0, isKeyframe = true, imageSignature = imageA,
            )
            every { tileDiffManager.computeTiledFrame(frame2) } returns TiledFrame(
                id = 2,
                tiles = listOf(
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x03), rect = IntRect(0, 0, 120, 22)),
                ),
                scale = 1f, originalSize = IntSize(120, 88),
                timestamp = 2L, orientation = 0, isKeyframe = false, imageSignature = imageB,
            )
            every { tileDiffManager.computeTiledFrame(frame3) } returns TiledFrame(
                id = 3,
                tiles = listOf(
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x04), rect = IntRect(0, 0, 60, 88)),
                    TiledFrame.Tile(bitmap = mockCompressibleBitmap(0x05), rect = IntRect(60, 0, 60, 88)),
                ),
                scale = 1f, originalSize = IntSize(120, 88),
                timestamp = 3L, orientation = 0, isKeyframe = false, imageSignature = imageA,
            )

            exportDiffManager.createCaptureEvent(frame1, session = "s")!!
            exportDiffManager.createCaptureEvent(frame2, session = "s")!!
            val export3 = exportDiffManager.createCaptureEvent(frame3, session = "s")!!

            assertTrue(export3.addImages.isEmpty(), "Backtrack should produce no adds")
            assertFalse(export3.removeImages.isNullOrEmpty(), "Backtrack should produce removes")
            assertTrue(
                export3.removeImages!!.all { it.imageSignature == imageB },
                "Backtrack removes must carry the rolled-back frame's ImageSignature",
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

    private fun mockCompressibleBitmap(firstByte: Int): Bitmap {
        val bitmap = mockk<Bitmap>()
        every { bitmap.isRecycled } returns false
        every { bitmap.recycle() } just runs
        every { bitmap.compress(any(), any(), any()) } answers {
            val stream = thirdArg<ByteArrayOutputStream>()
            stream.write(byteArrayOf(firstByte.toByte()))
            true
        }
        return bitmap
    }
}
