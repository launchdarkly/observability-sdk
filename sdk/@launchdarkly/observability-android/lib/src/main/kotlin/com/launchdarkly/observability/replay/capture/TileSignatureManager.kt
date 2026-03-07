package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap

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
        private const val TILE_W = 64
        private const val DEFAULT_PREFERRED_TILE_HEIGHT = 22

        private const val SEED_H0 = 0x517cc1b727220a95L
        private const val SEED_H1 = 0x6c62272e07bb0142L
        private const val SEED_H2 = -0x61c8864680b583ebL  // 0x9e3779b97f4a7c15
        private const val SEED_H3 = -0x40a7b892e31b1a47L  // 0xbf58476d1ce4e5b9
        private const val MIX_C1 = -0x00ae502812aa7333L    // 0xff51afd7ed558ccd
        private const val MIX_C2 = -0x3b314601e57a13adL    // 0xc4ceb9fe1a85ec53

    }

    @Volatile
    private var pixelBuffer: IntArray = IntArray(0)

    /**
     * Computes a tile-based signature with fixed 64-pixel tile width and
     * a height adjusted to a nearby divisor of the image height.
     * Uses native C implementation via JNI when available (NEON-accelerated on ARM),
     * falls back to Kotlin otherwise.
     */
    fun compute(bitmap: Bitmap): ImageSignature? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        if (TileHashNative.isAvailable) {
            TileHashNative.compute(bitmap)?.let { return it }
        }

        val tileHeight = nearestDivisor(height, DEFAULT_PREFERRED_TILE_HEIGHT, 22..44)
        return computeFixed64(bitmap, tileHeight)
    }

    /**
     * Computes a tile-based signature with explicitly provided tile dimensions.
     * No nearest-divisor adjustment is applied.
     */
    fun compute(bitmap: Bitmap, tileWidth: Int, tileHeight: Int): ImageSignature? {
        if (tileWidth <= 0 || tileHeight <= 0) return null
        return computeGeneric(bitmap, tileWidth, tileHeight)
    }

    /**
     * Convenience overload for square tiles. No nearest-divisor adjustment is applied.
     */
    fun compute(bitmap: Bitmap, tileSize: Int): ImageSignature? = compute(bitmap, tileSize, tileSize)

    private fun loadPixels(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val needed = w * h
        if (pixelBuffer.size < needed) {
            pixelBuffer = IntArray(needed)
        }
        val buf = pixelBuffer
        bitmap.getPixels(buf, 0, w, 0, 0, w, h)
        return buf
    }

    private fun computeFixed64(bitmap: Bitmap, tileHeight: Int): ImageSignature? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = loadPixels(bitmap)

        val columns = (width + TILE_W - 1) / TILE_W
        val rows = (height + tileHeight - 1) / tileHeight
        val fullCols = width / TILE_W
        val tileSignatures = ArrayList<TileSignature>(columns * rows)

        var tileAccHash = 0
        for (row in 0 until rows) {
            val startY = row * tileHeight
            val tileRows = minOf(tileHeight, height - startY)

            for (col in 0 until fullCols) {
                val sig = tileHashW64(pixels, width, col * TILE_W, startY, tileRows)
                tileSignatures.add(sig)
                tileAccHash = ImageSignature.accumulateTile(tileAccHash, sig)
            }

            if (fullCols < columns) {
                val startX = fullCols * TILE_W
                val sig = tileHashGeneric(pixels, width, startX, startY, width, startY + tileRows)
                tileSignatures.add(sig)
                tileAccHash = ImageSignature.accumulateTile(tileAccHash, sig)
            }
        }

        return ImageSignature.createWithAccHash(
            rows = rows, columns = columns,
            tileWidth = TILE_W, tileHeight = tileHeight,
            tileSignatures = tileSignatures, tileAccHash = tileAccHash,
        )
    }

    private fun computeGeneric(bitmap: Bitmap, tileWidth: Int, tileHeight: Int): ImageSignature? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = loadPixels(bitmap)

        val columns = (width + tileWidth - 1) / tileWidth
        val rows = (height + tileHeight - 1) / tileHeight
        val tileSignatures = ArrayList<TileSignature>(columns * rows)

        var tileAccHash = 0
        for (row in 0 until rows) {
            val startY = row * tileHeight
            val endY = minOf(startY + tileHeight, height)
            for (col in 0 until columns) {
                val startX = col * tileWidth
                val endX = minOf(startX + tileWidth, width)
                val sig = tileHashGeneric(pixels, width, startX, startY, endX, endY)
                tileSignatures.add(sig)
                tileAccHash = ImageSignature.accumulateTile(tileAccHash, sig)
            }
        }

        return ImageSignature.createWithAccHash(
            rows = rows, columns = columns,
            tileWidth = tileWidth, tileHeight = tileHeight,
            tileSignatures = tileSignatures, tileAccHash = tileAccHash,
        )
    }

    /**
     * Fast hash for full 64-pixel-wide tiles. The inner loop is fixed at 8 iterations
     * (8 pixels each = 64 pixels) with 4 parallel accumulators for ILP.
     */
    private fun tileHashW64(
        pixels: IntArray,
        imageWidth: Int,
        startX: Int,
        startY: Int,
        tileRows: Int,
    ): TileSignature {
        var h0 = SEED_H0; var h1 = SEED_H1
        var h2 = SEED_H2; var h3 = SEED_H3

        for (y in 0 until tileRows) {
            var idx = (startY + y) * imageWidth + startX
            for (i in 0 until 8) {
                h0 += packNativePair(pixels[idx], pixels[idx + 1])
                h1 += packNativePair(pixels[idx + 2], pixels[idx + 3])
                h2 += packNativePair(pixels[idx + 4], pixels[idx + 5])
                h3 += packNativePair(pixels[idx + 6], pixels[idx + 7])
                idx += 8
            }
            h0 = h0 xor h2; h1 = h1 xor h3
            h2 += h0; h3 += h1
        }

        h0 = h0 xor h2; h1 = h1 xor h3
        h0 = h0 xor (h0 ushr 33); h0 *= MIX_C1; h0 = h0 xor (h0 ushr 33)
        h1 = h1 xor (h1 ushr 29); h1 *= MIX_C2; h1 = h1 xor (h1 ushr 29)
        return TileSignature(hashLo = h0, hashHi = h1)
    }

    /**
     * Generic hash for tiles of any width. Uses the same 4-accumulator scheme
     * with 8-pixel processing groups and remainder handling.
     */
    private fun tileHashGeneric(
        pixels: IntArray,
        imageWidth: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
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
                h0 += packNativePair(pixels[idx], pixels[idx + 1])
                h1 += packNativePair(pixels[idx + 2], pixels[idx + 3])
                h2 += packNativePair(pixels[idx + 4], pixels[idx + 5])
                h3 += packNativePair(pixels[idx + 6], pixels[idx + 7])
                idx += 8
            }

            if (remPairs >= 1) h0 += packNativePair(pixels[idx], pixels[idx + 1])
            if (remPairs >= 2) h1 += packNativePair(pixels[idx + 2], pixels[idx + 3])
            if (remPairs >= 3) h2 += packNativePair(pixels[idx + 4], pixels[idx + 5])
            if (hasTail) h3 += toNativeWord(pixels[idx + remPairs * 2])

            h0 = h0 xor h2; h1 = h1 xor h3
            h2 += h0; h3 += h1
        }

        h0 = h0 xor h2; h1 = h1 xor h3
        h0 = h0 xor (h0 ushr 33); h0 *= MIX_C1; h0 = h0 xor (h0 ushr 33)
        h1 = h1 xor (h1 ushr 29); h1 *= MIX_C2; h1 = h1 xor (h1 ushr 29)
        return TileSignature(hashLo = h0, hashHi = h1)
    }

    /**
     * Android's getPixels returns ARGB words (0xAARRGGBB), while native hashing reads
     * RGBA_8888 bytes as little-endian 32-bit words (0xAABBGGRR). Normalize to native
     * word layout so Kotlin and JNI paths produce identical hashes.
     */
    private fun toNativeWord(argb: Int): Long {
        val native = (argb and 0xFF00FF00.toInt()) or
            ((argb and 0x00FF0000) ushr 16) or
            ((argb and 0x000000FF) shl 16)
        return native.toLong() and 0xFFFFFFFFL
    }

    private fun packNativePair(firstArgb: Int, secondArgb: Int): Long =
        toNativeWord(firstArgb) or (toNativeWord(secondArgb) shl 32)

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
