package com.launchdarkly.observability.replay.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION
import android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION
import androidx.annotation.RequiresApi
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.replay.masking.MaskCollector
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import androidx.core.graphics.withTranslation
import com.launchdarkly.observability.replay.masking.Mask
import androidx.core.graphics.createBitmap
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.calculateScaleFactor
import com.launchdarkly.observability.replay.masking.MaskApplier
import com.launchdarkly.observability.replay.scaleCoordinate

/**
 * A source of [ExportFrame]s taken from the lowest visible window. Captures
 * are emitted on the [captureFlow] property of this class.
 *
 * @param sessionManager Used to get current session for tagging [ExportFrame] with session id
 */
class CaptureSource(
    private val sessionManager: SessionManager,
    private val options: ReplayOptions,
    private val logger: LDLogger,
    // TODO: O11Y-628 - add captureQuality options
) {
    data class CaptureResult(
        val windowEntry: WindowEntry,
        val bitmap: Bitmap,
    )

    private val _captureEventFlow = MutableSharedFlow<ExportFrame>()
    val captureFlow: SharedFlow<ExportFrame> = _captureEventFlow.asSharedFlow()
    private val windowInspector = WindowInspector(logger)
    private val maskCollector = MaskCollector(logger)
    private val maskApplier = MaskApplier()
    private val maskMatchers = options.privacyProfile.asMatchersList()
    private val tileSignatureManager = TileSignatureManager()

    @Volatile
    private var tileSignature: TileSignature? = null

    /**
     * Requests a [ExportFrame] be taken now.
     */
    suspend fun captureNow() {
        val capture = doCapture()
        if (capture != null) {
            _captureEventFlow.emit(capture)
        }
    }

    /**
     * Internal capture routine.
     */
    private suspend fun doCapture(): ExportFrame? =
        withContext(DispatcherProviderHolder.current.main) {
            // Synchronize with UI rendering frame
            suspendCancellableCoroutine { continuation ->
                Choreographer.getInstance().postFrameCallback {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }

            val timestamp = System.currentTimeMillis()
            val session = sessionManager.getSessionId()

            val windowsEntries = windowInspector.appWindows()
            if (windowsEntries.isEmpty()) {
                return@withContext null
            }

            val baseIndex = pickBaseWindow(windowsEntries) ?: return@withContext null
            val baseWindowEntry = windowsEntries[baseIndex]
            val rect = baseWindowEntry.rect()

            val scaleFactor = calculateScaleFactor(options.scale, baseWindowEntry.rootView)

            // protect against race condition where decor view has no size
            if (rect.right <= 0 || rect.bottom <= 0) {
                return@withContext null
            }

            // TODO: O11Y-625 - optimize memory allocations
            // TODO: O11Y-625 - see if holding bitmap is more efficient than base64 encoding immediately after compression
            // TODO: O11Y-628 - use captureQuality option for scaling and adjust this bitmap accordingly, may need to investigate power of 2 rounding for performance

            val capturingWindowEntries = windowsEntries.subList(baseIndex, windowsEntries.size)

            val beforeMasks = collectMasks(capturingWindowEntries)

            val captureResults: MutableList<CaptureResult?> = MutableList(capturingWindowEntries.size) { null }
            try {
                var captured = 0
                for (i in capturingWindowEntries.indices) {
                    val windowEntry = capturingWindowEntries[i]
                    val captureResult = captureViewResult(
                        windowEntry,
                        scaleFactor = scaleFactor
                    )
                    if (captureResult == null) {
                        if (i == 0) {
                            return@withContext null
                        }
                        beforeMasks[i] = null
                        continue
                    }

                    captured++
                    captureResults[i] = captureResult
                }
                if (captured == 0) {
                    return@withContext null
                }

                // Synchronize with UI rendering frame
                suspendCancellableCoroutine { continuation ->
                    Choreographer.getInstance().postFrameCallback {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }

                val afterMasks = collectMasksFromResults(captureResults)

                // off the main thread to avoid blocking the UI thread
                return@withContext withContext(DispatcherProviderHolder.current.default) {
                    val baseResult = captureResults[0] ?: return@withContext null

                    val mergedMasks = maskApplier.mergeMasksMap(beforeMasks, afterMasks)
                        ?: run {
                            // Mask instability is expected during animations/scrolling; ensure we always
                            // recycle already-captured bitmaps before bailing out to avoid native OOM.
                            return@withContext null
                        }

                    // if need to draw something on base bitmap additionally
                    if (captureResults.size > 1 || (mergedMasks.isNotEmpty() && mergedMasks[0] != null)) {
                        val canvas = Canvas(baseResult.bitmap)
                        mergedMasks[0]?.let { maskApplier.drawMasks(canvas, it, scaleFactor = scaleFactor) }

                        for (i in 1 until captureResults.size) {
                            val res = captureResults[i] ?: continue
                            val entry = res.windowEntry
                            val dx = (entry.screenLeft - baseWindowEntry.screenLeft).toFloat() * scaleFactor
                            val dy = (entry.screenTop - baseWindowEntry.screenTop).toFloat() * scaleFactor

                            canvas.withTranslation(dx, dy) {
                                drawBitmap(res.bitmap, 0f, 0f, null)
                                mergedMasks[i]?.let { maskApplier.drawMasks(canvas, it, scaleFactor = scaleFactor) }
                            }
                            if (!res.bitmap.isRecycled) {
                                res.bitmap.recycle()
                            }
                        }
                    }

                    val newSignature = tileSignatureManager.compute(baseResult.bitmap, 64)
                    if (newSignature != null && newSignature == tileSignature) {
                        // the similar bitmap not send
                        return@withContext null
                    }
                    tileSignature = newSignature

                    createCaptureEvent(baseResult.bitmap, timestamp, session)
                }
            } finally {
                recycleCaptureResults(captureResults)
            }
        }

    private fun recycleCaptureResults(captureResults: List<CaptureResult?>) {
        for (res in captureResults) {
            val bitmap = res?.bitmap ?: continue
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun collectMasks(capturingWindowEntries: List<WindowEntry>): MutableList<List<Mask>?> {
        return capturingWindowEntries.map {
            maskCollector.collectMasks( it.rootView, maskMatchers)
        }.toMutableList()
    }

    private fun collectMasksFromResults(captureResults: List<CaptureResult?>): MutableList<List<Mask>?> {
        return captureResults.map { result ->
            result?.windowEntry?.rootView?.let { rv -> maskCollector.collectMasks(rv, maskMatchers) }
        }.toMutableList()
    }

    private fun pickBaseWindow(windowsEntries: List<WindowEntry>): Int? {
        val appIdx = windowsEntries.indexOfFirst {
            val wmType = it.layoutParams?.type ?: 0
            wmType == TYPE_APPLICATION || wmType == TYPE_BASE_APPLICATION
        }
        if (appIdx >= 0) return appIdx

        val activityIdx = windowsEntries.indexOfFirst { it.type == WindowType.ACTIVITY }
        if (activityIdx >= 0) return activityIdx

        val dialogIdx = windowsEntries.indexOfFirst { it.type == WindowType.DIALOG }
        if (dialogIdx >= 0) return dialogIdx

        // Fallback to the first available
        return if (windowsEntries.isNotEmpty()) 0 else null
    }

    private suspend fun captureViewResult(windowEntry: WindowEntry, scaleFactor: Float): CaptureResult? {
        val bitmap = captureViewBitmap(windowEntry, scaleFactor) ?: return null
        return CaptureResult(windowEntry, bitmap)
    }

    private suspend fun captureViewBitmap(windowEntry: WindowEntry, scaleFactor: Float): Bitmap? {
        val view = windowEntry.rootView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && windowEntry.isPixelCopyCandidate()) {
            val window = windowInspector.findWindow(view)
            if (window != null) {
                pixelCopy(window, view, windowEntry.rect(), scaleFactor)?.let {
                    return it
                }
            }
        }

        // Fallback if window not available or old version
        return withContext(Dispatchers.Main.immediate) {
            if (!view.isAttachedToWindow || !view.isShown) return@withContext null

            return@withContext canvasDrawBitmap(view, scaleFactor)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pixelCopy(
        window: Window,
        view: View,
        rect: Rect,
        scaleFactor: Float
    ): Bitmap? {
        val bitmap = createBitmapForView(view, scaleFactor) ?: return null

        val result = suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            try {
                PixelCopy.request(
                    window,
                    rect,
                    bitmap,
                    { copyResult ->
                        if (!continuation.isActive) {
                            bitmap.recycle()
                            return@request
                        }
                        if (copyResult == PixelCopy.SUCCESS) {
                            continuation.resume(bitmap)
                        } else {
                            continuation.resume(null)
                        }
                    }, handler
                )
            } catch (t: Throwable) {
                // It could normally happen when view is being closed during screenshot
                logger.warn("Failed to capture window", t)
                continuation.resume(null)
            }
        }

        if (result == null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return result
    }

    private fun canvasDrawBitmap(
        view: View,
        scaleFactor: Float
    ): Bitmap? {
        val bitmap = createBitmapForView(view, scaleFactor) ?: return null

        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)

        try {
            view.draw(canvas)
        } catch (t: Throwable) {
            logger.warn("Failed to draw Canvas. This view might be better processed by PixelCopy", t)
            bitmap.recycle()
            return null
        }
        finally {
            canvas.restore()
        }

        return bitmap
    }

    private fun createBitmapForView(view: View, scaleFactor: Float): Bitmap? {
        val width = scaleCoordinate(view.width.toFloat(), scaleFactor)
        val height = scaleCoordinate(view.height.toFloat(), scaleFactor)
        if (width <= 0 || height <= 0) {
            logger.warn("Cannot draw view with zero dimensions: ${view.width}x${view.height}")
            return null
        }
        return createBitmap(width, height)
    }

    private fun createCaptureEvent(
        postMask: Bitmap,
        timestamp: Long,
        session: String
    ): ExportFrame {
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
                        rect = ExportFrame.FrameRect(
                            left = 0,
                            top = 0,
                            width = width,
                            height = height
                        ),
                        tileSignature = tileSignature
                    )
                ),
                removeImages = null,
                originalSize = ExportFrame.FrameSize(
                    width = width,
                    height = height
                ),
                scale = 1f,
                format = ExportFrame.ExportFormat.Webp(quality = 30),
                timestamp = timestamp,
                orientation = 0,
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
