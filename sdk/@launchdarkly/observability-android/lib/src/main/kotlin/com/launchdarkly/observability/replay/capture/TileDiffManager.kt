package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap
import com.launchdarkly.observability.replay.ReplayOptions
import kotlin.math.roundToInt

class TileDiffManager(
    private val compression: ReplayOptions.CompressionMethod,
    private val scale: Double,
    private val tileSignatureManager: TileSignatureManager = TileSignatureManager(),
) {
    private var previousSignature: ImageSignature? = null
    private var incrementalSnapshots = 0
    private var frameId = 0

    fun computeTiledFrame(frame: RawFrame): TiledFrame? {
        val frameWidth = frame.bitmap.width
        val frameHeight = frame.bitmap.height
        val replayScale = scale.takeIf { it > 0.0 } ?: 1.0
        val replayWidth = (frameWidth / replayScale).roundToInt()
        val replayHeight = (frameHeight / replayScale).roundToInt()
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

        val isKeyframe = when (val method = compression) {
            is ReplayOptions.CompressionMethod.OverlayTiles -> {
                if (method.layers <= 0) {
                    true
                } else {
                    incrementalSnapshots = (incrementalSnapshots + 1) % method.layers
                    if (incrementalSnapshots == 0) {
                        true
                    } else {
                        val needWholeScreen =
                            diffRect.width >= frameWidth && diffRect.height >= frameHeight
                        if (needWholeScreen) incrementalSnapshots = 0
                        needWholeScreen
                    }
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
                width = replayWidth,
                height = replayHeight,
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
            finalBitmap = try {
                Bitmap.createBitmap(
                    frame.bitmap,
                    diffRect.left,
                    diffRect.top,
                    croppedWidth,
                    croppedHeight
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
            // Bitmap diffing and cropping use pixels, while RRWeb lays images out in
            // logical coordinates. Match Swift by converting only the exported tile
            // rectangle; the encoded bitmap retains its full pixel resolution.
            finalRect = IntRect(
                left = (diffRect.left / replayScale).roundToInt(),
                top = (diffRect.top / replayScale).roundToInt(),
                width = (croppedWidth / replayScale).roundToInt(),
                height = (croppedHeight / replayScale).roundToInt(),
            )
        }

        val imageSignatureForTransfer = when (compression) {
            is ReplayOptions.CompressionMethod.OverlayTiles -> imageSignature
            is ReplayOptions.CompressionMethod.ScreenImage -> null
        }

        return TiledFrame(
            id = frameId,
            tiles = listOf(TiledFrame.Tile(bitmap = finalBitmap, rect = finalRect)),
            scale = scale,
            originalSize = IntSize(width = replayWidth, height = replayHeight),
            timestamp = frame.timestamp,
            orientation = frame.orientation,
            isKeyframe = isKeyframe,
            imageSignature = imageSignatureForTransfer,
        )
    }
}
