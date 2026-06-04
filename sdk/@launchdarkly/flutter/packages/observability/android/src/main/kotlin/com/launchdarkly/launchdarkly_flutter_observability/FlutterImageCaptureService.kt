package com.launchdarkly.launchdarkly_flutter_observability

import android.graphics.Bitmap
import com.launchdarkly.observability.replay.capture.ImageCaptureService
import com.launchdarkly.observability.replay.capture.ImageCaptureServicing
import io.flutter.plugin.common.MethodChannel
import java.nio.ByteBuffer
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal class FlutterImageCaptureService(
    private val channel: MethodChannel,
    private val maskTextInputs: Boolean,
) : ImageCaptureServicing {

    override suspend fun captureRawFrame(): ImageCaptureService.RawFrame? {
        val payload = withContext(Dispatchers.Main.immediate) {
            requestCapture()
        } ?: return null

        return withContext(Dispatchers.Default) {
            val bytes = payload["bytes"] as? ByteArray ?: return@withContext null
            val width = (payload["width"] as? Number)?.toInt() ?: return@withContext null
            val height = (payload["height"] as? Number)?.toInt() ?: return@withContext null
            val bitmap = bitmapFromRGBA(bytes, width, height) ?: return@withContext null
            val timestamp = (payload["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val orientation = (payload["orientation"] as? Number)?.toInt() ?: 0

            ImageCaptureService.RawFrame(
                bitmap = bitmap,
                timestamp = timestamp,
                orientation = orientation,
            )
        }
    }

    /**
     * Wraps Flutter's raw RGBA bytes (8 bits/channel, premultiplied alpha,
     * row-major) into an [ARGB_8888][Bitmap.Config.ARGB_8888] bitmap, whose
     * in-memory byte layout is also RGBA. Avoids decoding a PNG on the wire.
     */
    private fun bitmapFromRGBA(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0 || bytes.size < width * height * 4) {
            return null
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return bitmap
    }

    private suspend fun requestCapture(): Map<*, *>? =
        suspendCancellableCoroutine { continuation ->
            channel.invokeMethod(
                METHOD_CAPTURE_FRAME,
                mapOf(ARG_MASK_TEXT_INPUTS to maskTextInputs),
                object : MethodChannel.Result {
                    override fun success(result: Any?) {
                        if (continuation.isActive) {
                            continuation.resume(result as? Map<*, *>)
                        }
                    }

                    override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                        logger.warning(
                            "Flutter screenshot capture failed: $errorCode ${errorMessage.orEmpty()}",
                        )
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                    override fun notImplemented() {
                        logger.warning("Flutter screenshot capture is not registered.")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                },
            )
        }

    private companion object {
        const val METHOD_CAPTURE_FRAME = "captureFrame"
        const val ARG_MASK_TEXT_INPUTS = "maskTextInputs"
        private val logger = Logger.getLogger("FlutterImageCaptureService")
    }
}
