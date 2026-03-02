package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap

data class TileSignature(
    val hashLo: Long,
    val hashHi: Long,
) {
    constructor(hash: Long) : this(hashLo = hash, hashHi = 0L)
}

data class ImageSignature(
    val rows: Int,
    val columns: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val tileSignatures: List<TileSignature>,
)

/**
 * Computes tile-based signatures for bitmaps.
 *
 * This class is intentionally not thread-safe in order to reuse a single internal
 * pixel buffer allocation and minimize memory churn and GC pressure. Do not invoke
 * methods on the same instance from multiple threads concurrently. If cross-thread
 * use is required, create one instance per thread or guard access with external
 * synchronization.
 */
class TileSignatureManager {
    companion object {
        private const val DEFAULT_PREFERRED_TILE_WIDTH = 64
        private const val DEFAULT_PREFERRED_TILE_HEIGHT = 22

        private val nativeAvailable: Boolean = try {
            System.loadLibrary("tilehash")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    private external fun nativeComputeAllTileHashes(
        pixels: IntArray, width: Int, height: Int,
        tileWidth: Int, tileHeight: Int
    ): LongArray

    @Volatile
    private var pixelBuffer: IntArray = IntArray(0)

    /**
     * Computes a tile-based signature using preferred defaults that are adjusted to nearby divisors.
     */
    fun compute(bitmap: Bitmap): ImageSignature? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return null
        }
        val tileWidth = nearestDivisor(width, DEFAULT_PREFERRED_TILE_WIDTH, 60..79)
        val tileHeight = nearestDivisor(height, DEFAULT_PREFERRED_TILE_HEIGHT, 22..44)
        return computeInternal(bitmap, tileWidth, tileHeight)
    }

    /**
     * Computes a tile-based signature with explicitly provided tile dimensions.
     * No nearest-divisor adjustment is applied.
     */
    fun compute(bitmap: Bitmap, tileWidth: Int, tileHeight: Int): ImageSignature? {
        if (tileWidth <= 0 || tileHeight <= 0) return null
        return computeInternal(bitmap, tileWidth, tileHeight)
    }

    /**
     * Convenience overload for square tiles. No nearest-divisor adjustment is applied.
     */
    fun compute(bitmap: Bitmap, tileSize: Int): ImageSignature? = compute(bitmap, tileSize, tileSize)

    private fun computeInternal(
        bitmap: Bitmap,
        tileWidth: Int,
        tileHeight: Int
    ): ImageSignature? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return null
        }

        val pixelsNeeded = width * height
        if (pixelBuffer.size < pixelsNeeded) {
            pixelBuffer = IntArray(pixelsNeeded)
        }
        val pixels = pixelBuffer
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val tilesX = (width + tileWidth - 1) / tileWidth
        val tilesY = (height + tileHeight - 1) / tileHeight
        val tileCount = tilesX * tilesY

        val tileSignatures = if (nativeAvailable) {
            val hashes = nativeComputeAllTileHashes(pixels, width, height, tileWidth, tileHeight)
            ArrayList<TileSignature>(tileCount).apply {
                for (i in 0 until tileCount) {
                    val offset = i * 2
                    add(TileSignature(hashLo = hashes[offset], hashHi = hashes[offset + 1]))
                }
            }
        } else {
            computeAllTileHashesKotlin(pixels, width, height, tileWidth, tileHeight, tilesX, tilesY)
        }

        return ImageSignature(
            rows = tilesY,
            columns = tilesX,
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            tileSignatures = tileSignatures,
        )
    }

    private fun computeAllTileHashesKotlin(
        pixels: IntArray,
        width: Int,
        height: Int,
        tileWidth: Int,
        tileHeight: Int,
        tilesX: Int,
        tilesY: Int,
    ): ArrayList<TileSignature> {
        val tileSignatures = ArrayList<TileSignature>(tilesX * tilesY)
        for (ty in 0 until tilesY) {
            val startY = ty * tileHeight
            val endY = minOf(startY + tileHeight, height)
            for (tx in 0 until tilesX) {
                val startX = tx * tileWidth
                val endX = minOf(startX + tileWidth, width)
                tileSignatures.add(tileHashKotlin(pixels, width, startX, startY, endX, endY))
            }
        }
        return tileSignatures
    }

    private fun tileHashKotlin(
        pixels: IntArray,
        width: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int
    ): TileSignature {
        var hashLo = 5163949831757626579L
        var hashHi = 4657936482115123397L
        val primeLo = 1238197591667094937L
        val primeHi = 1700294137212722571L

        val pixelCount = endX - startX
        val pairCount = pixelCount ushr 1
        val hasTrailingPixel = pixelCount and 1 != 0

        for (y in startY until endY) {
            var i = y * width + startX

            for (p in 0 until pairCount) {
                val v = (pixels[i].toLong() and 0xFFFFFFFFL) or
                        ((pixels[i + 1].toLong() and 0xFFFFFFFFL) shl 32)
                hashLo = (hashLo xor v) * primeLo
                hashHi = (hashHi xor v) * primeHi
                i += 2
            }

            if (hasTrailingPixel) {
                val v = pixels[i].toLong() and 0xFFFFFFFFL
                hashLo = (hashLo xor v) * primeLo
                hashHi = (hashHi xor v) * primeHi
            }
        }
        return TileSignature(hashLo = hashLo, hashHi = hashHi)
    }

    private fun nearestDivisor(value: Int, preferred: Int, range: IntRange): Int {
        if (value <= 0) return preferred

        fun isDivisor(candidate: Int): Boolean = candidate > 0 && value % candidate == 0

        if (preferred in range && isDivisor(preferred)) return preferred

        val maxDistance = maxOf(
            kotlin.math.abs(range.first - preferred),
            kotlin.math.abs(range.last - preferred)
        )

        for (offset in 1..maxDistance) {
            val positive = preferred + offset
            if (positive in range && isDivisor(positive)) return positive

            val negative = preferred - offset
            if (negative in range && isDivisor(negative)) return negative
        }

        return preferred
    }

}

fun ImageSignature.diffRectangle(other: ImageSignature?): IntRect? {
    if (other == null ||
        rows != other.rows ||
        columns != other.columns ||
        tileWidth != other.tileWidth ||
        tileHeight != other.tileHeight
    ) {
        return IntRect(
            left = 0,
            top = 0,
            width = columns * tileWidth,
            height = rows * tileHeight,
        )
    }

    var minRow = Int.MAX_VALUE
    var maxRow = Int.MIN_VALUE
    var minColumn = Int.MAX_VALUE
    var maxColumn = Int.MIN_VALUE

    for (idx in tileSignatures.indices) {
        if (tileSignatures[idx] == other.tileSignatures[idx]) continue
        val row = idx / columns
        val col = idx % columns
        minRow = minOf(minRow, row)
        maxRow = maxOf(maxRow, row)
        minColumn = minOf(minColumn, col)
        maxColumn = maxOf(maxColumn, col)
    }

    if (minRow == Int.MAX_VALUE) return null

    return IntRect(
        left = minColumn * tileWidth,
        top = minRow * tileHeight,
        width = (maxColumn - minColumn + 1) * tileWidth,
        height = (maxRow - minRow + 1) * tileHeight,
    )
}
