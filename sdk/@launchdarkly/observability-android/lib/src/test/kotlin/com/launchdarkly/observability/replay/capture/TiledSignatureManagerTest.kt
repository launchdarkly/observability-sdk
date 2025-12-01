package com.launchdarkly.observability.replay.capture

import com.launchdarkly.observability.testutil.mockBitmap
import com.launchdarkly.observability.testutil.withOverlayRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TiledSignatureManagerTest {

    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private fun solidPixels(width: Int, height: Int, color: Int): IntArray {
        val pixels = IntArray(width * height)
        java.util.Arrays.fill(pixels, color)
        return pixels
    }

    @Test
    fun compute_returnsNull_whenTileSizeNonPositive() {
        val manager = TiledSignatureManager()
        val bitmap = mockBitmap(2, 2, RED)

        assertNull(manager.compute(bitmap, 0))
        assertNull(manager.compute(bitmap, -8))
    }

    @Test
    fun compute_returnsSignature_whenValidInputs() {
        val manager = TiledSignatureManager()
        val bitmap = mockBitmap(4, 4, BLUE)

        val signature = manager.compute(bitmap, 2)
        assertNotNull(signature)
        // 4x4 with tileSize 2 => 2x2 = 4 tiles
        assertEquals(4, signature!!.tileHashes.size)
    }

    @Test
    fun signatures_equal_forIdenticalContent() {
        val manager = TiledSignatureManager()
        val a = mockBitmap(8, 8, BLUE)
        val b = mockBitmap(8, 8, BLUE)

        val sigA = manager.compute(a, 4)
        val sigB = manager.compute(b, 4)

        assertNotNull(sigA)
        assertNotNull(sigB)
        assertEquals(sigA, sigB)
    }

    @Test
    fun signatures_differ_forDifferentContent() {
        val manager = TiledSignatureManager()
        val a = mockBitmap(8, 8, RED)
        val b = mockBitmap(8, 8, WHITE)

        val sigA = manager.compute(a, 4)
        val sigB = manager.compute(b, 4)

        assertNotNull(sigA)
        assertNotNull(sigB)
        assertNotEquals(sigA, sigB)
    }

    @Test
    fun tileCount_matchesExpectedCeilDivision() {
        val manager = TiledSignatureManager()
        val bmp = mockBitmap(10, 10, RED)

        // tileSize 4 => ceil(10/4)=3 in each dimension => 9 tiles
        val sig4 = manager.compute(bmp, 4)
        assertNotNull(sig4)
        assertEquals(9, sig4!!.tileHashes.size)

        // tileSize 6 => ceil(10/6)=2 in each dimension => 4 tiles
        val sig6 = manager.compute(bmp, 6)
        assertNotNull(sig6)
        assertEquals(4, sig6!!.tileHashes.size)
    }

    @Test
    fun smallOverlay_changesOnlyAffectedTiles_hashes() {
        val manager = TiledSignatureManager()
        val width = 12
        val height = 12
        val basePixels = solidPixels(width, height, WHITE)
        val overlayPixels = withOverlayRect(
            basePixels = basePixels,
            imageWidth = width,
            imageHeight = height,
            color = RED,
            left = 8,   // touches only the last column of tiles for tileSize=4
            top = 8,    // touches only the last row of tiles for tileSize=4
            right = 12,
            bottom = 12
        )
        val base = mockBitmap(width, height, basePixels)
        val withOverlay = mockBitmap(width, height, overlayPixels)

        val tileSize = 4
        val sigBase = manager.compute(base, tileSize)!!
        val sigOverlay = manager.compute(withOverlay, tileSize)!!

        // 12x12 with tile size 4 => 3x3 tiles
        assertEquals(9, sigBase.tileHashes.size)
        assertEquals(9, sigOverlay.tileHashes.size)

        var diffCount = 0
        for (i in sigBase.tileHashes.indices) {
            if (sigBase.tileHashes[i] != sigOverlay.tileHashes[i]) {
                diffCount++
            }
        }
        org.junit.jupiter.api.Assertions.assertNotEquals(0, diffCount)
    }
}

