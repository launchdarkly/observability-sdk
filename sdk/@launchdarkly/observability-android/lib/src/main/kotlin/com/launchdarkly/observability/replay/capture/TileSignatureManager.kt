package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap

/**
 * Computes tile-based signatures for bitmaps using the native C implementation via JNI
 * (NEON-accelerated on ARM). Requires the `session_replay_c` shared library.
 */
class TileSignatureManager {
    fun compute(bitmap: Bitmap): ImageSignature? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        if (!TileHashNative.isAvailable) return null
        return TileHashNative.compute(bitmap)
    }
}
