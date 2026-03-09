package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap

internal object TileHashNative {
    val isAvailable: Boolean

    init {
        isAvailable = try {
            System.loadLibrary("tile_hash")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    /**
     * Computes tile signatures natively via JNI.
     * Returns a packed LongArray: [rows, columns, tileWidth, tileHeight, hashLo0, hashHi0, ...]
     * Returns null if the bitmap cannot be processed.
     */
    @JvmStatic
    external fun nativeCompute(bitmap: Bitmap): LongArray?

    fun compute(bitmap: Bitmap): ImageSignature? {
        val packed = nativeCompute(bitmap) ?: return null
        if (packed.size < 4) return null

        val rows = packed[0].toInt()
        val columns = packed[1].toInt()
        val tileWidth = packed[2].toInt()
        val tileHeight = packed[3].toInt()
        val totalTiles = rows * columns
        if (packed.size < 4 + totalTiles * 2) return null

        val tileSignatures = ArrayList<TileSignature>(totalTiles)
        var tileAccHash = 0
        for (i in 0 until totalTiles) {
            val sig = TileSignature(
                hashLo = packed[4 + i * 2],
                hashHi = packed[4 + i * 2 + 1],
            )
            tileSignatures.add(sig)
            tileAccHash = ImageSignature.accumulateTile(tileAccHash, sig)
        }

        return ImageSignature.createWithAccHash(
            rows = rows, columns = columns,
            tileWidth = tileWidth, tileHeight = tileHeight,
            tileSignatures = tileSignatures, tileAccHash = tileAccHash,
        )
    }
}
