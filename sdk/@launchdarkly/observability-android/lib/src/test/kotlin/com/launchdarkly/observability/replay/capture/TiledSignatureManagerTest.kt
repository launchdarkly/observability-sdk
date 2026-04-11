package com.launchdarkly.observability.replay.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
}
