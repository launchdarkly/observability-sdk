package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap

data class TiledSignature(
    val tileHashes: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TiledSignature

        if (!tileHashes.contentEquals(other.tileHashes)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        return tileHashes.contentHashCode()
    }
}

class TiledSignatureManager {
    private var pixelBuffer: IntArray = IntArray(0)

    fun compute(
        bitmap: Bitmap,
        tileSize: Int
    ): TiledSignature? {
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
        return TiledSignature(tileHashes)
    }

    private fun hashTile(
        pixels: IntArray,
        width: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int
    ): Long {
        var hash: Long = 5163949831757626579
        val prime: Long = 1238197591667094937 // from https://bigprimes.org
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


