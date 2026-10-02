package com.launchdarkly.observability.replay.capture

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
