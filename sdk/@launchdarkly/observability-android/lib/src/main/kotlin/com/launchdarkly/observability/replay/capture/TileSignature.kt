package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap

data class TileSignature(
    val hash: Long
)

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
    @Volatile
    private var pixelBuffer: IntArray = IntArray(0)

/**
 * Computes a tile-based signature for the given bitmap. Not thread-safe.
 *
 * @param bitmap The bitmap to compute a signature for.
 * @param preferredTileWidth Preferred tile width.
 * @param preferredTileHeight Preferred tile height.
 * @return The tile signature.
 */
    fun compute(
        bitmap: Bitmap,
        preferredTileWidth: Int = 64,
        preferredTileHeight: Int = 22
    ): ImageSignature? {
        if (preferredTileWidth <= 0 || preferredTileHeight <= 0) return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return null
        }
        val tileWidth = nearestDivisor(width, preferredTileWidth, 60..79)
        val tileHeight = nearestDivisor(height, preferredTileHeight, 22..44)

        val pixelsNeeded = width * height
        if (pixelBuffer.size < pixelsNeeded) {
            pixelBuffer = IntArray(pixelsNeeded)
        }
        val pixels = pixelBuffer
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val tilesX = (width + tileWidth - 1) / tileWidth
        val tilesY = (height + tileHeight - 1) / tileHeight
        val tileCount = tilesX * tilesY
        val tileSignatures = ArrayList<TileSignature>(tileCount)

        for (ty in 0 until tilesY) {
            val startY = ty * tileHeight
            val endY = minOf(startY + tileHeight, height)

            for (tx in 0 until tilesX) {
                val startX = tx * tileWidth
                val endX = minOf(startX + tileWidth, width)
                tileSignatures.add(
                    TileSignature(
                        hash = hashTile(
                            pixels = pixels,
                            width = width,
                            startX = startX,
                            startY = startY,
                            endX = endX,
                            endY = endY
                        )
                    )
                )
            }
        }

        return ImageSignature(
            rows = tilesY,
            columns = tilesX,
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            tileSignatures = tileSignatures,
        )
    }

    private fun hashTile(
        pixels: IntArray,
        width: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int
    ): Long {
        var hash = 5163949831757626579L
        val prime = 1238197591667094937L // from https://bigprimes.org
        for (y in startY until endY) {
            val rowOffset = y * width
            for (x in startX until endX) {
                val argb = pixels[rowOffset + x]
                hash = (hash xor (argb and 0xFF).toLong()) * prime
                hash = (hash xor ((argb ushr 8) and 0xFF).toLong()) * prime
                hash = (hash xor ((argb ushr 16) and 0xFF).toLong()) * prime
                hash = (hash xor ((argb ushr 24) and 0xFF).toLong()) * prime
            }
        }
        return hash
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
