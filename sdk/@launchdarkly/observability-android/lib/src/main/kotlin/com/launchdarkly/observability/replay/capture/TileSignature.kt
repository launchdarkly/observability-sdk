package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap

data class TileSignature(
    val tileHashes: LongArray
) {
    override fun equals(other: Any?): Boolean = 
        other is TileSignature && tileHashes.contentEquals(other.tileHashes)
    
        override fun hashCode(): Int = tileHashes.contentHashCode()
}

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
 * @param tileSize The size of the tiles to use for the signature.
 * @return The tile signature.
 */
    fun compute(
        bitmap: Bitmap,
        tileSize: Int
    ): TileSignature? {
        if (tileSize <= 0) return null
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

        val tilesX = (width + tileSize - 1) / tileSize
        val tilesY = (height + tileSize - 1) / tileSize
        val tileCount = tilesX * tilesY
        val tileHashes = LongArray(tileCount)

        var tileIndex = 0
        for(ty in 0 until tilesY) {
            val startY = ty * tileSize
            val endY = minOf(startY + tileSize, height)

            for(tx in 0 until tilesX) {
                val startX = tx * tileSize
                val endX = minOf(startX + tileSize, width)
                tileHashes[tileIndex] = hashTile(
                    pixels = pixels,
                    width = width,
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY
                )
                tileIndex++
            }
        }

        //TODO: optimize memory allocations here to have 2 arrays instead of 1
        return TileSignature(tileHashes)
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
        for(y in startY until endY) {
            val rowOffset = y * width
            for(x in startX until endX) {
                val argb = pixels[rowOffset + x]
                hash = (hash xor (argb and 0xFF).toLong()) * prime
                hash = (hash xor ((argb ushr 8) and 0xFF).toLong()) * prime
                hash = (hash xor ((argb ushr 16) and 0xFF).toLong()) * prime
                hash = (hash xor ((argb ushr 24) and 0xFF).toLong()) * prime
            }
        }
        return hash
    }
}
