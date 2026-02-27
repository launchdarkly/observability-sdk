package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap

data class TiledFrame(
    val id: Int,
    val tiles: List<Tile>,
    val scale: Float,
    val originalSize: IntSize,
    val timestamp: Long,
    val orientation: Int,
    val isKeyframe: Boolean,
    val imageSignature: ImageSignature?,
) {
    data class Tile(
        val bitmap: Bitmap,
        val rect: IntRect,
    )

    fun recycleBitmaps() {
        tiles.forEach { tile ->
            if (!tile.bitmap.isRecycled) {
                tile.bitmap.recycle()
            }
        }
    }
}
