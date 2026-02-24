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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class ExportDiffManagerTest {

    @Test
    fun `backtracking emits remove-only frame on rollback`() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(byteArrayOf(0x01.toByte()), Base64.NO_WRAP) } returns "AQ=="
        every { Base64.encodeToString(byteArrayOf(0x02.toByte()), Base64.NO_WRAP) } returns "Ag=="
        every { Base64.encodeToString(byteArrayOf(0x03.toByte()), Base64.NO_WRAP) } returns "Aw=="
        try {
            val tileDiffManager = mockk<TileDiffManager>()
            val exportDiffManager = ExportDiffManager(
                compression = ReplayOptions.CompressionMethod.OverlayTiles(backtracking = true),
                scale = 1f,
                tileDiffManager = tileDiffManager,
            )

            val tileA = TileSignature(101L)
            val tileB = TileSignature(202L)
            val imageA = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(tileA))
            val imageB = ImageSignature(rows = 1, columns = 1, tileWidth = 64, tileHeight = 22, tileSignatures = listOf(tileB))

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
            assertEquals(tileB, export3.removeImages!!.first().tileSignature)
        } finally {
            unmockkStatic(Base64::class)
        }
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
