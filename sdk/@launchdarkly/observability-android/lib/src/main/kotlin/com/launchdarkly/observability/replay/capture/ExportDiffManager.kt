package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap
import android.util.Base64
import com.launchdarkly.observability.replay.ReplayOptions
import java.io.ByteArrayOutputStream

class ExportDiffManager(
    private val compression: ReplayOptions.CompressionMethod,
    private val tileSignatureManager: TileSignatureManager = TileSignatureManager(),
) {
    @Volatile
    private var tileSignature: TileSignature? = null

    fun createCaptureEvent(rawFrame: ImageCaptureService.RawFrame, session: String): ExportFrame? {
        val newSignature = tileSignatureManager.compute(rawFrame.bitmap, 64, 22)
        if (newSignature != null && newSignature == tileSignature) {
            // the similar bitmap not send
            if (!rawFrame.bitmap.isRecycled) {
                rawFrame.bitmap.recycle()
            }
            return null
        }
        tileSignature = newSignature

        return createCaptureEventInternal(rawFrame, session)
    }

    private fun createCaptureEventInternal(rawFrame: ImageCaptureService.RawFrame, session: String): ExportFrame {
        val postMask = rawFrame.bitmap
        // TODO: O11Y-625 - optimize memory allocations here, re-use byte arrays and such
        val outputStream = ByteArrayOutputStream()
        return try {
            // TODO: O11Y-628 - calculate quality using captureQuality options
            postMask.compress(
                Bitmap.CompressFormat.WEBP,
                30,
                outputStream
            )
            val byteArray = outputStream.toByteArray()
            val compressedImage =
                Base64.encodeToString(
                    byteArray,
                    Base64.NO_WRAP
                )

            val width = postMask.width
            val height = postMask.height
            ExportFrame(
                keyFrameId = 0,
                addImages = listOf(
                    ExportFrame.AddImage(
                        imageBase64 = compressedImage,
                        rect = IntRect(
                            left = 0,
                            top = 0,
                            width = width,
                            height = height
                        ),
                        tileSignature = tileSignature
                    )
                ),
                removeImages = null,
                originalSize = IntSize(
                    width = width,
                    height = height
                ),
                scale = 1f,
                format = ExportFrame.ExportFormat.Webp(quality = 30),
                timestamp = rawFrame.timestamp,
                orientation = rawFrame.orientation,
                isKeyframe = true,
                imageSignature = null,
                session = session
            )
        } finally {
            try {
                outputStream.close()
            } catch (_: Throwable) {
            }
            try {
                postMask.recycle()
            } catch (_: Throwable) {
            }
        }
    }
}
