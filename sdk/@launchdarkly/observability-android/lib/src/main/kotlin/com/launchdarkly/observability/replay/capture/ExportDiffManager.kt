package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap
import android.util.Base64
import com.launchdarkly.observability.replay.ReplayOptions
import java.io.ByteArrayOutputStream

class ExportDiffManager(
    private val compression: ReplayOptions.CompressionMethod,
    private val scale: Float = 1f,
    private val tileDiffManager: TileDiffManager = TileDiffManager(compression = compression, scale = scale),
) {
    private val currentImages = mutableListOf<ExportFrame.RemoveImage>()
    private val currentImagesIndex = mutableMapOf<ImageSignature, Int>()
    private val signatureLock = Any()
    private val format = ExportFrame.ExportFormat.Webp(quality = 30)
    private var keyFrameId = 0

    fun createCaptureEvent(rawFrame: ImageCaptureService.RawFrame, session: String): ExportFrame? {
        synchronized(signatureLock) {
            val tiledFrame = tileDiffManager.computeTiledFrame(rawFrame) ?: return null
            return createCaptureEventInternal(tiledFrame, session)
        }
    }

    private fun createCaptureEventInternal(tiledFrame: TiledFrame, session: String): ExportFrame? {
        try {
            val adds = mutableListOf<ExportFrame.AddImage>()
            var removes = mutableListOf<ExportFrame.RemoveImage>()

            if (tiledFrame.isKeyframe) {
                removes = currentImages.toMutableList()
                currentImages.clear()
                currentImagesIndex.clear()
                keyFrameId += 1
            }

            val signature = tiledFrame.imageSignature
            val useBacktracking =
                compression is ReplayOptions.CompressionMethod.OverlayTiles &&
                    compression.backtracking

            if (signature != null && useBacktracking) {
                val lastKeyNodeIdx = currentImagesIndex[signature]
                if (lastKeyNodeIdx != null && lastKeyNodeIdx < currentImages.size) {
                    removes = currentImages.subList(lastKeyNodeIdx + 1, currentImages.size).toMutableList()
                    currentImages.subList(lastKeyNodeIdx + 1, currentImages.size).clear()

                    val filtered = currentImagesIndex.filterValues { value -> value > lastKeyNodeIdx }
                    currentImagesIndex.clear()
                    currentImagesIndex.putAll(filtered)
                } else {
                    for ((tileIdx, tile) in tiledFrame.tiles.withIndex()) {
                        val tileSignature = signature.tileSignatures.getOrNull(tileIdx)
                        val addImage =
                            tile.bitmap.asExportedImage(format = format, rect = tile.rect, tileSignature = tileSignature)
                                ?: return null
                        adds.add(addImage)
                        if (tileSignature != null) {
                            currentImages.add(
                                ExportFrame.RemoveImage(
                                    keyFrameId = keyFrameId,
                                    tileSignature = tileSignature,
                                )
                            )
                        }
                    }
                    currentImagesIndex[signature] = currentImages.size - 1
                }
            } else {
                for ((tileIdx, tile) in tiledFrame.tiles.withIndex()) {
                    val tileSignature = signature?.tileSignatures?.getOrNull(tileIdx)
                    val addImage = tile.bitmap.asExportedImage(format = format, rect = tile.rect, tileSignature = tileSignature)
                        ?: return null
                    adds.add(addImage)
                    if (tileSignature != null) {
                        currentImages.add(
                            ExportFrame.RemoveImage(
                                keyFrameId = keyFrameId,
                                tileSignature = tileSignature,
                            )
                        )
                    }
                }
                if (signature != null) {
                    currentImagesIndex[signature] = currentImages.size - 1
                }
            }

            if (adds.isEmpty() && removes.isEmpty()) {
                return null
            }

            return ExportFrame(
                keyFrameId = keyFrameId,
                addImages = adds,
                removeImages = removes,
                originalSize = tiledFrame.originalSize,
                scale = tiledFrame.scale,
                format = format,
                timestamp = tiledFrame.timestamp,
                orientation = tiledFrame.orientation,
                isKeyframe = tiledFrame.isKeyframe,
                imageSignature = tiledFrame.imageSignature,
                session = session,
            )
        } finally {
            tiledFrame.recycleBitmaps()
        }
    }
}

private fun Bitmap.asExportedImage(
    format: ExportFrame.ExportFormat,
    rect: IntRect,
    tileSignature: TileSignature?,
): ExportFrame.AddImage? {
    val outputStream = ByteArrayOutputStream()
    return try {
        val compressionOk = when (format) {
            ExportFrame.ExportFormat.Png -> compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            is ExportFrame.ExportFormat.Jpeg -> compress(
                Bitmap.CompressFormat.JPEG,
                (format.quality * 100).toInt().coerceIn(0, 100),
                outputStream
            )
            is ExportFrame.ExportFormat.Webp -> compress(
                Bitmap.CompressFormat.WEBP,
                format.quality.coerceIn(0, 100),
                outputStream
            )
        }
        if (!compressionOk) {
            return null
        }
        val compressedImage = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        ExportFrame.AddImage(
            imageBase64 = compressedImage,
            rect = rect,
            tileSignature = tileSignature,
        )
    } finally {
        outputStream.close()
    }
}
