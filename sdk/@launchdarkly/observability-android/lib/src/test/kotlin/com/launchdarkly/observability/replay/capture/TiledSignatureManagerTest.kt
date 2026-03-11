package com.launchdarkly.observability.replay.capture

import com.launchdarkly.observability.testutil.mockBitmap
import com.launchdarkly.observability.testutil.withOverlayRect
import java.util.Arrays
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TileSignatureManagerTest {

    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()

    private val SEED_H0 = 0x517cc1b727220a95L
    private val SEED_H1 = 0x6c62272e07bb0142L
    private val SEED_H2 = -0x61c8864680b583ebL
    private val SEED_H3 = -0x40a7b892e31b1a47L
    private val MIX_C1 = -0x00ae502812aa7333L
    private val MIX_C2 = -0x3b314601e57a13adL

    private fun solidPixels(width: Int, height: Int, color: Int): IntArray {
        val pixels = IntArray(width * height)
        Arrays.fill(pixels, color)
        return pixels
    }

    private fun nativeWordFromArgb(argb: Int): Long {
        val nativeWord = (argb and 0xFF00FF00.toInt()) or
            ((argb and 0x00FF0000) ushr 16) or
            ((argb and 0x000000FF) shl 16)
        return nativeWord.toLong() and 0xFFFFFFFFL
    }

    private fun packPair(firstArgb: Int, secondArgb: Int, normalizeForNativeLayout: Boolean): Long {
        val first = if (normalizeForNativeLayout) nativeWordFromArgb(firstArgb) else firstArgb.toLong() and 0xFFFFFFFFL
        val second = if (normalizeForNativeLayout) nativeWordFromArgb(secondArgb) else secondArgb.toLong() and 0xFFFFFFFFL
        return first or (second shl 32)
    }

    private fun hashGenericTile(
        pixels: IntArray,
        imageWidth: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        normalizeForNativeLayout: Boolean,
    ): TileSignature {
        val pixelWidth = endX - startX
        val quads = pixelWidth ushr 3
        val remPixels = pixelWidth and 7
        val remPairs = remPixels ushr 1
        val hasTail = remPixels and 1 != 0

        var h0 = SEED_H0; var h1 = SEED_H1
        var h2 = SEED_H2; var h3 = SEED_H3

        for (y in startY until endY) {
            var idx = y * imageWidth + startX

            for (q in 0 until quads) {
                h0 += packPair(pixels[idx], pixels[idx + 1], normalizeForNativeLayout)
                h1 += packPair(pixels[idx + 2], pixels[idx + 3], normalizeForNativeLayout)
                h2 += packPair(pixels[idx + 4], pixels[idx + 5], normalizeForNativeLayout)
                h3 += packPair(pixels[idx + 6], pixels[idx + 7], normalizeForNativeLayout)
                idx += 8
            }

            if (remPairs >= 1) h0 += packPair(pixels[idx], pixels[idx + 1], normalizeForNativeLayout)
            if (remPairs >= 2) h1 += packPair(pixels[idx + 2], pixels[idx + 3], normalizeForNativeLayout)
            if (remPairs >= 3) h2 += packPair(pixels[idx + 4], pixels[idx + 5], normalizeForNativeLayout)
            if (hasTail) {
                h3 += if (normalizeForNativeLayout) {
                    nativeWordFromArgb(pixels[idx + remPairs * 2])
                } else {
                    pixels[idx + remPairs * 2].toLong() and 0xFFFFFFFFL
                }
            }

            h0 = h0 xor h2; h1 = h1 xor h3
            h2 += h0; h3 += h1
        }

        h0 = h0 xor h2; h1 = h1 xor h3
        h0 = h0 xor (h0 ushr 33); h0 *= MIX_C1; h0 = h0 xor (h0 ushr 33)
        h1 = h1 xor (h1 ushr 29); h1 *= MIX_C2; h1 = h1 xor (h1 ushr 29)
        return TileSignature(hashLo = h0, hashHi = h1)
    }

    private fun expectedDefaultTileHeight(height: Int): Int {
        val preferred = 22
        val range = 22..44
        if (height <= 0) return preferred
        if (preferred in range && height % preferred == 0) return preferred
        val maxDistance = maxOf(kotlin.math.abs(range.first - preferred), kotlin.math.abs(range.last - preferred))
        for (offset in 1..maxDistance) {
            val positive = preferred + offset
            if (positive in range && positive > 0 && height % positive == 0) return positive
            val negative = preferred - offset
            if (negative in range && negative > 0 && height % negative == 0) return negative
        }
        return preferred
    }

    @Test
    fun `compute returns null when tile size is non positive`() {
        val manager = TileSignatureManager()
        val bitmap = mockBitmap(2, 2, RED)

        assertNull(manager.compute(bitmap, 0))
        assertNull(manager.compute(bitmap, -8))
    }

    @Test
    fun `compute returns signature when inputs are valid`() {
        val manager = TileSignatureManager()
        val bitmap = mockBitmap(4, 4, BLUE)

        val signature = manager.compute(bitmap, 2)
        assertNotNull(signature)
        // 4x4 with tileSize 2 => 2x2 = 4 tiles
        assertEquals(4, signature!!.tileSignatures.size)
    }

    @Test
    fun `signatures are equal for identical content`() {
        val manager = TileSignatureManager()
        val a = mockBitmap(8, 8, BLUE)
        val b = mockBitmap(8, 8, BLUE)

        val sigA = manager.compute(a, 4)
        val sigB = manager.compute(b, 4)

        assertNotNull(sigA)
        assertNotNull(sigB)
        assertEquals(sigA, sigB)
    }

    @Test
    fun `signatures differ for different content`() {
        val manager = TileSignatureManager()
        val a = mockBitmap(8, 8, RED)
        val b = mockBitmap(8, 8, WHITE)

        val sigA = manager.compute(a, 4)
        val sigB = manager.compute(b, 4)

        assertNotNull(sigA)
        assertNotNull(sigB)
        assertNotEquals(sigA, sigB)
    }

    @Test
    fun `tile count matches expected ceil division`() {
        val manager = TileSignatureManager()
        val bmp = mockBitmap(10, 10, RED)

        // tileSize 4 => ceil(10/4)=3 in each dimension => 9 tiles
        val sig4 = manager.compute(bmp, 4)
        assertNotNull(sig4)
        assertEquals(9, sig4!!.tileSignatures.size)

        // tileSize 6 => ceil(10/6)=2 in each dimension => 4 tiles
        val sig6 = manager.compute(bmp, 6)
        assertNotNull(sig6)
        assertEquals(4, sig6!!.tileSignatures.size)
    }

    @Test
    fun `small overlay changes only affected tiles hashes`() {
        val manager = TileSignatureManager()
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
        assertEquals(9, sigBase.tileSignatures.size)
        assertEquals(9, sigOverlay.tileSignatures.size)

        var diffCount = 0
        for (i in sigBase.tileSignatures.indices) {
            if (sigBase.tileSignatures[i] != sigOverlay.tileSignatures[i]) {
                diffCount++
            }
        }
        assertNotEquals(0, diffCount)
    }

    @Test
    fun `default compute uses fixed 64 width and divisor-based height`() {
        val manager = TileSignatureManager()
        val bitmap = mockBitmap(130, 88, BLUE)

        val signature = manager.compute(bitmap)
        assertNotNull(signature)
        assertEquals(64, signature!!.tileWidth)
        assertEquals(22, signature.tileHeight)
        assertEquals(3, signature.columns) // ceil(130/64)
        assertEquals(4, signature.rows)    // ceil(88/22)
    }

    @Test
    fun `default fixed64 path matches explicit generic path`() {
        val manager = TileSignatureManager()
        val width = 130
        val height = 88
        val pixels = IntArray(width * height) { i ->
            // deterministic non-uniform pattern
            (0xFF shl 24) or ((i * 17) and 0x00FFFFFF)
        }
        val bitmap = mockBitmap(width, height, pixels)

        val defaultSig = manager.compute(bitmap)
        val explicitSig = manager.compute(bitmap, 64, 22)

        assertNotNull(defaultSig)
        assertNotNull(explicitSig)
        assertEquals(explicitSig, defaultSig)
    }

    @Test
    fun `default fixed64 path matches explicit path across content patterns`() {
        val manager = TileSignatureManager()
        val cases = listOf(
            Triple(64, 22, 0x000000),   // exact single tile
            Triple(128, 44, 0xFFFFFF),  // exact multi-tile
            Triple(191, 67, 0x123456),  // partial right-edge and bottom tile
        )

        for ((width, height, seed) in cases) {
            val pixels = IntArray(width * height) { i ->
                val v = (seed + i * 1315423911).toInt()
                (0xFF shl 24) or (v and 0x00FFFFFF)
            }
            val bitmap = mockBitmap(width, height, pixels)
            val tileHeight = expectedDefaultTileHeight(height)

            val defaultSig = manager.compute(bitmap)
            val explicitSig = manager.compute(bitmap, 64, tileHeight)
            assertNotNull(defaultSig)
            assertNotNull(explicitSig)
            assertEquals(explicitSig, defaultSig)
        }
    }

    @Test
    fun `kotlin hashing matches native byte order instead of raw ARGB words`() {
        val manager = TileSignatureManager()
        val width = 3
        val height = 2
        val pixels = intArrayOf(
            0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt(),
            0xFF99AA00.toInt(), 0xFF00CC44.toInt(), 0xFF3300EE.toInt(),
        )
        val bitmap = mockBitmap(width, height, pixels)

        val signature = manager.compute(bitmap, width, height)
        assertNotNull(signature)
        assertEquals(1, signature!!.tileSignatures.size)

        val expectedNative = hashGenericTile(
            pixels = pixels,
            imageWidth = width,
            startX = 0,
            startY = 0,
            endX = width,
            endY = height,
            normalizeForNativeLayout = true,
        )
        val legacyArgbDirect = hashGenericTile(
            pixels = pixels,
            imageWidth = width,
            startX = 0,
            startY = 0,
            endX = width,
            endY = height,
            normalizeForNativeLayout = false,
        )

        assertNotEquals(legacyArgbDirect, expectedNative)
        assertEquals(expectedNative, signature.tileSignatures.single())
    }
}

