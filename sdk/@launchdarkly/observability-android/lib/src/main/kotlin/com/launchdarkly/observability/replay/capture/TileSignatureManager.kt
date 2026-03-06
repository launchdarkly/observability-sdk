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
) {
    private var _hashCode: Int = 0

    override fun hashCode(): Int {
        var h = _hashCode
        if (h == 0) {
            h = finalizeHash(rows, columns, tileWidth, tileHeight, accumulateHash(tileSignatures))
            _hashCode = h
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageSignature) return false
        val h = _hashCode
        val oh = other._hashCode
        if (h != 0 && oh != 0 && h != oh) return false
        return rows == other.rows &&
            columns == other.columns &&
            tileWidth == other.tileWidth &&
            tileHeight == other.tileHeight &&
            tileSignatures == other.tileSignatures
    }

    companion object {
        internal fun accumulateTile(acc: Int, sig: TileSignature): Int =
            31 * acc + (sig.hashLo xor sig.hashHi).toInt()

        private fun accumulateHash(tiles: List<TileSignature>): Int {
            var acc = 0
            for (sig in tiles) acc = accumulateTile(acc, sig)
            return acc
        }

        private fun finalizeHash(rows: Int, columns: Int, tileWidth: Int, tileHeight: Int, tileAcc: Int): Int {
            var h = rows
            h = 31 * h + columns
            h = 31 * h + tileWidth
            h = 31 * h + tileHeight
            h = 31 * h + tileAcc
            return if (h == 0) 1 else h
        }

        internal fun createWithAccHash(
            rows: Int, columns: Int, tileWidth: Int, tileHeight: Int,
            tileSignatures: List<TileSignature>, tileAccHash: Int,
        ): ImageSignature = ImageSignature(rows, columns, tileWidth, tileHeight, tileSignatures).also {
            it._hashCode = finalizeHash(rows, columns, tileWidth, tileHeight, tileAccHash)
        }
    }
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
    companion object {
        private const val TILE_W = 64
        private const val DEFAULT_PREFERRED_TILE_HEIGHT = 22

        private const val SEED_H0 = 0x517cc1b727220a95L
        private const val SEED_H1 = 0x6c62272e07bb0142L
        private const val SEED_H2 = -0x61c88646805b83ebL  // 0x9e3779b97f4a7c15
        private const val SEED_H3 = -0x40a7b892e31b1a47L  // 0xbf58476d1ce4e5b9
        private const val MIX_C1 = -0x00ae502812aa7333L    // 0xff51afd7ed558ccd
        private const val MIX_C2 = -0x3b314601e57a13adL    // 0xc4ceb9fe1a85ec53

        private fun pack(lo: Int, hi: Int): Long =
            (lo.toLong() and 0xFFFFFFFFL) or ((hi.toLong() and 0xFFFFFFFFL) shl 32)

        private fun avalanche(h0: Long, h1: Long, h2: Long, h3: Long): TileSignature {
            var a = h0 xor h2
            var b = h1 xor h3
            a = a xor (a ushr 33); a *= MIX_C1; a = a xor (a ushr 33)
            b = b xor (b ushr 29); b *= MIX_C2; b = b xor (b ushr 29)
            return TileSignature(hashLo = a, hashHi = b)
        }
    }

    @Volatile
    private var pixelBuffer: IntArray = IntArray(0)

    /**
     * Computes a tile-based signature with fixed 64-pixel tile width and
     * a height adjusted to a nearby divisor of the image height.
     */
    fun compute(bitmap: Bitmap): ImageSignature? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
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
                h0 += pack(pixels[idx], pixels[idx + 1])
                h1 += pack(pixels[idx + 2], pixels[idx + 3])
                h2 += pack(pixels[idx + 4], pixels[idx + 5])
                h3 += pack(pixels[idx + 6], pixels[idx + 7])
                idx += 8
            }
            h0 = h0 xor h2; h1 = h1 xor h3
            h2 += h0; h3 += h1
        }

        return avalanche(h0, h1, h2, h3)
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
                h0 += pack(pixels[idx], pixels[idx + 1])
                h1 += pack(pixels[idx + 2], pixels[idx + 3])
                h2 += pack(pixels[idx + 4], pixels[idx + 5])
                h3 += pack(pixels[idx + 6], pixels[idx + 7])
                idx += 8
            }

            if (remPairs >= 1) h0 += pack(pixels[idx], pixels[idx + 1])
            if (remPairs >= 2) h1 += pack(pixels[idx + 2], pixels[idx + 3])
            if (remPairs >= 3) h2 += pack(pixels[idx + 4], pixels[idx + 5])
            if (hasTail) h3 += pixels[idx + remPairs * 2].toLong() and 0xFFFFFFFFL

            h0 = h0 xor h2; h1 = h1 xor h3
            h2 += h0; h3 += h1
        }

        return avalanche(h0, h1, h2, h3)
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
