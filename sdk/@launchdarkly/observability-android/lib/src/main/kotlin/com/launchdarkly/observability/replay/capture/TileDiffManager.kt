package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap
import com.launchdarkly.observability.replay.ReplayOptions

class TileDiffManager(
    private val compression: ReplayOptions.CompressionMethod,
    private val scale: Float,
    private val tileSignatureManager: TileSignatureManager = TileSignatureManager(),
) {
    private var previousSignature: ImageSignature? = null
    private var incrementalSnapshots = 0
    private var frameId = 0

    fun computeTiledFrame(frame: ImageCaptureService.RawFrame): TiledFrame? {
        val frameWidth = frame.bitmap.width
        val frameHeight = frame.bitmap.height
        val imageSignature = tileSignatureManager.compute(frame.bitmap) ?: run {
            if (!frame.bitmap.isRecycled) {
                frame.bitmap.recycle()
            }
            return null
        }

        frameId += 1
        val diffRect = imageSignature.diffRectangle(previousSignature) ?: run {
            if (!frame.bitmap.isRecycled) {
                frame.bitmap.recycle()
            }
            return null
        }
        previousSignature = imageSignature

        val needWholeScreen =
            diffRect.width >= frameWidth && diffRect.height >= frameHeight

        val isKeyframe = when (val method = compression) {
            is ReplayOptions.CompressionMethod.OverlayTiles -> {
                if (method.layers > 0) {
                    incrementalSnapshots = (incrementalSnapshots + 1) % method.layers
                    val keyframe = needWholeScreen || incrementalSnapshots == 0
                    if (needWholeScreen) {
                        incrementalSnapshots = 0
                    }
                    keyframe
                } else {
                    true
                }
            }

            is ReplayOptions.CompressionMethod.ScreenImage -> true
        }

        val finalRect: IntRect
        val finalBitmap: Bitmap
        if (isKeyframe) {
            finalBitmap = frame.bitmap
            finalRect = IntRect(
                left = 0,
                top = 0,
                width = frameWidth,
                height = frameHeight,
            )
        } else {
            val croppedWidth = minOf(frameWidth - diffRect.left, diffRect.width)
            val croppedHeight = minOf(frameHeight - diffRect.top, diffRect.height)
            if (croppedWidth <= 0 || croppedHeight <= 0) {
                if (!frame.bitmap.isRecycled) {
                    frame.bitmap.recycle()
                }
                return null
            }
            finalRect = IntRect(
                left = diffRect.left,
                top = diffRect.top,
                width = croppedWidth,
                height = croppedHeight,
            )
            finalBitmap = try {
                Bitmap.createBitmap(
                    frame.bitmap,
                    finalRect.left,
                    finalRect.top,
                    finalRect.width,
                    finalRect.height
                )
            } catch (_: Throwable) {
                if (!frame.bitmap.isRecycled) {
                    frame.bitmap.recycle()
                }
                return null
            }
            if (!frame.bitmap.isRecycled) {
                frame.bitmap.recycle()
            }
        }

        val imageSignatureForTransfer = when (compression) {
            is ReplayOptions.CompressionMethod.OverlayTiles -> imageSignature
            is ReplayOptions.CompressionMethod.ScreenImage -> null
        }

        return TiledFrame(
            id = frameId,
            tiles = listOf(TiledFrame.Tile(bitmap = finalBitmap, rect = finalRect)),
            scale = scale,
            originalSize = IntSize(width = frameWidth, height = frameHeight),
            timestamp = frame.timestamp,
            orientation = frame.orientation,
            isKeyframe = isKeyframe,
            imageSignature = imageSignatureForTransfer,
        )
    }
}
