package com.launchdarkly.observability.replay.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TileSignatureManagerTest {

    private fun sig(hashLo: Long, hashHi: Long) = TileSignature(hashLo, hashHi)

    private fun imageSignature(
        rows: Int,
        columns: Int,
        tileWidth: Int,
        tileHeight: Int,
        tiles: List<TileSignature>,
    ) = ImageSignature(rows, columns, tileWidth, tileHeight, tiles)

    @Test
    fun `identical signatures are equal`() {
        val tiles = listOf(sig(1L, 2L), sig(3L, 4L))
        val a = imageSignature(1, 2, 64, 22, tiles)
        val b = imageSignature(1, 2, 64, 22, tiles)
        assertEquals(a, b)
    }

    @Test
    fun `signatures with different tile hashes are not equal`() {
        val a = imageSignature(1, 2, 64, 22, listOf(sig(1L, 2L), sig(3L, 4L)))
        val b = imageSignature(1, 2, 64, 22, listOf(sig(1L, 2L), sig(5L, 6L)))
        assertNotEquals(a, b)
    }

    @Test
    fun `signatures with different dimensions are not equal`() {
        val tiles = listOf(sig(1L, 2L))
        val a = imageSignature(1, 1, 64, 22, tiles)
        val b = imageSignature(1, 1, 64, 44, tiles)
        assertNotEquals(a, b)
    }

    @Test
    fun `signatures with different grid layout are not equal`() {
        val tiles = listOf(sig(1L, 2L), sig(3L, 4L))
        val a = imageSignature(1, 2, 64, 22, tiles)
        val b = imageSignature(2, 1, 64, 22, tiles)
        assertNotEquals(a, b)
    }

    @Test
    fun `small overlay changes only affected tiles`() {
        // 3x3 grid, all tiles identical except bottom-right
        val baseTiles = List(9) { sig(42L, 99L) }
        val overlayTiles = baseTiles.toMutableList().apply {
            this[8] = sig(100L, 200L)
        }

        val sigBase = imageSignature(3, 3, 4, 4, baseTiles)
        val sigOverlay = imageSignature(3, 3, 4, 4, overlayTiles)

        assertEquals(9, sigBase.tileSignatures.size)
        assertEquals(9, sigOverlay.tileSignatures.size)

        var diffCount = 0
        for (i in sigBase.tileSignatures.indices) {
            if (sigBase.tileSignatures[i] != sigOverlay.tileSignatures[i]) {
                diffCount++
            }
        }
        assertEquals(1, diffCount)

        // diffRectangle should only cover the bottom-right tile
        val diff = sigOverlay.diffRectangle(sigBase)
        assertNotNull(diff)
        assertEquals(IntRect(8, 8, 4, 4), diff)
    }

    @Test
    fun `overlay in the middle changes only affected tiles`() {
        // 3x3 grid, middle tile changed
        val baseTiles = List(9) { sig(it.toLong(), 0L) }
        val overlayTiles = baseTiles.toMutableList().apply {
            this[4] = sig(999L, 999L) // center tile (row=1, col=1)
        }

        val sigBase = imageSignature(3, 3, 10, 10, baseTiles)
        val sigOverlay = imageSignature(3, 3, 10, 10, overlayTiles)

        var diffCount = 0
        for (i in sigBase.tileSignatures.indices) {
            if (sigBase.tileSignatures[i] != sigOverlay.tileSignatures[i]) {
                diffCount++
            }
        }
        assertEquals(1, diffCount)

        val diff = sigOverlay.diffRectangle(sigBase)
        assertNotNull(diff)
        assertEquals(IntRect(10, 10, 10, 10), diff)
    }

    @Test
    fun `diffRectangle returns full rect when comparing against null`() {
        val sig = imageSignature(2, 3, 64, 22, List(6) { sig(it.toLong(), 0L) })
        val diff = sig.diffRectangle(null)
        assertEquals(IntRect(0, 0, 3 * 64, 2 * 22), diff)
    }

    @Test
    fun `diffRectangle returns null for identical signatures`() {
        val tiles = listOf(sig(1L, 2L), sig(3L, 4L))
        val a = imageSignature(1, 2, 64, 22, tiles)
        val b = imageSignature(1, 2, 64, 22, tiles)
        assertNull(a.diffRectangle(b))
    }

    @Test
    fun `diffRectangle returns full rect when dimensions differ`() {
        val a = imageSignature(2, 3, 64, 22, List(6) { sig(it.toLong(), 0L) })
        val b = imageSignature(3, 2, 64, 22, List(6) { sig(it.toLong(), 0L) })
        val diff = a.diffRectangle(b)
        assertEquals(IntRect(0, 0, 3 * 64, 2 * 22), diff)
    }

    @Test
    fun `diffRectangle returns full rect when tile sizes differ`() {
        val tiles = List(4) { sig(it.toLong(), 0L) }
        val a = imageSignature(2, 2, 64, 22, tiles)
        val b = imageSignature(2, 2, 32, 22, tiles)
        val diff = a.diffRectangle(b)
        assertEquals(IntRect(0, 0, 2 * 64, 2 * 22), diff)
    }

    @Test
    fun `diffRectangle detects single changed tile`() {
        val tilesA = listOf(sig(1L, 0L), sig(2L, 0L), sig(3L, 0L), sig(4L, 0L))
        val tilesB = listOf(sig(1L, 0L), sig(2L, 0L), sig(3L, 0L), sig(99L, 0L))
        val a = imageSignature(2, 2, 64, 22, tilesA)
        val b = imageSignature(2, 2, 64, 22, tilesB)
        val diff = a.diffRectangle(b)
        assertEquals(IntRect(64, 22, 64, 22), diff)
    }

    @Test
    fun `diffRectangle spans multiple changed tiles`() {
        val tilesA = listOf(
            sig(1L, 0L), sig(2L, 0L), sig(3L, 0L),
            sig(4L, 0L), sig(5L, 0L), sig(6L, 0L),
        )
        val tilesB = listOf(
            sig(99L, 0L), sig(2L, 0L), sig(3L, 0L),
            sig(4L, 0L), sig(5L, 0L), sig(88L, 0L),
        )
        val a = imageSignature(2, 3, 10, 10, tilesA)
        val b = imageSignature(2, 3, 10, 10, tilesB)
        val diff = a.diffRectangle(b)
        assertEquals(IntRect(0, 0, 30, 20), diff)
    }

    @Test
    fun `diffRectangle covers row strip for row-only changes`() {
        // 3x3 grid, only middle row changes
        val tilesA = List(9) { sig(it.toLong(), 0L) }
        val tilesB = tilesA.toMutableList().apply {
            this[3] = sig(90L, 0L)
            this[4] = sig(91L, 0L)
            this[5] = sig(92L, 0L)
        }
        val a = imageSignature(3, 3, 10, 10, tilesA)
        val b = imageSignature(3, 3, 10, 10, tilesB)
        val diff = a.diffRectangle(b)
        assertEquals(IntRect(0, 10, 30, 10), diff)
    }

    @Test
    fun `diffRectangle covers column strip for column-only changes`() {
        // 3x3 grid, only middle column changes
        val tilesA = List(9) { sig(it.toLong(), 0L) }
        val tilesB = tilesA.toMutableList().apply {
            this[1] = sig(90L, 0L)
            this[4] = sig(91L, 0L)
            this[7] = sig(92L, 0L)
        }
        val a = imageSignature(3, 3, 10, 10, tilesA)
        val b = imageSignature(3, 3, 10, 10, tilesB)
        val diff = a.diffRectangle(b)
        assertEquals(IntRect(10, 0, 10, 30), diff)
    }

    @Test
    fun `hashCode is consistent for equal signatures`() {
        val tiles = listOf(sig(42L, 99L))
        val a = imageSignature(1, 1, 64, 22, tiles)
        val b = imageSignature(1, 1, 64, 22, tiles)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `hashCode differs for different signatures`() {
        val a = imageSignature(1, 1, 64, 22, listOf(sig(1L, 2L)))
        val b = imageSignature(1, 1, 64, 22, listOf(sig(3L, 4L)))
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `createWithAccHash produces equal signature to constructor`() {
        val tiles = listOf(sig(1L, 2L), sig(3L, 4L))
        var acc = 0
        for (tile in tiles) acc = ImageSignature.accumulateTile(acc, tile)

        val fromConstructor = ImageSignature(1, 2, 64, 22, tiles)
        val fromFactory = ImageSignature.createWithAccHash(1, 2, 64, 22, tiles, acc)

        assertEquals(fromConstructor, fromFactory)
        assertEquals(fromConstructor.hashCode(), fromFactory.hashCode())
    }

    @Test
    fun `single tile signature convenience constructor sets hashHi to zero`() {
        val sig = TileSignature(hash = 42L)
        assertEquals(42L, sig.hashLo)
        assertEquals(0L, sig.hashHi)
    }
}
