package com.launchdarkly.observability.replay.capture

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION
import android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.replay.masking.MaskMatcher
import com.launchdarkly.observability.replay.masking.SensitiveAreasCollector
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.core.graphics.withTranslation

/**
 * A source of [CaptureEvent]s taken from the most recently resumed [Activity]s window. Captures
 * are emitted on the [captureFlow] property of this class.
 *
 * @param sessionManager Used to get current session for tagging [CaptureEvent] with session id
 */
class CaptureSource(
    private val sessionManager: SessionManager,
    private val maskMatchers: List<MaskMatcher>,
    private val logger: LDLogger,
    // TODO: O11Y-628 - add captureQuality options
) {
    data class CaptureResult(
        val windowEntry: WindowEntry,
        val bitmap: Bitmap,
        val masks: List<ComposeRect>
    )

    private val _captureEventFlow = MutableSharedFlow<CaptureEvent>()
    val captureFlow: SharedFlow<CaptureEvent> = _captureEventFlow.asSharedFlow()
    private val windowInspector = WindowInspector(logger)
    private val sensitiveAreasCollector = SensitiveAreasCollector(logger)

    /**
     * Requests a [CaptureEvent] be taken now.
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
    private suspend fun doCapture(): CaptureEvent? =
        withContext(DispatcherProviderHolder.current.main) {
            // val activity = _mostRecentActivity ?: return@withContext null // return if no activity
            //  val window = activity.window ?: return@withContext null // return if activity has no window
            val windowsEntries = windowInspector.appWindows()
            if (windowsEntries.isEmpty()) {
                return@withContext null
            }

            val baseWindowEntry = pickBaseWindow(windowsEntries) ?: return@withContext null
            val baseView = baseWindowEntry.rootView
            val rect = baseWindowEntry.rect()

            // protect against race condition where decor view has no size
            if (rect.right <= 0 || rect.bottom <= 0) {
                return@withContext null
            }

            // TODO: O11Y-625 - optimize memory allocations
            // TODO: O11Y-625 - see if holding bitmap is more efficient than base64 encoding immediately after compression
            // TODO: O11Y-628 - use captureQuality option for scaling and adjust this bitmap accordingly, may need to investigate power of 2 rounding for performance
            // Create a bitmap with the window dimensions
            val timestamp = System.currentTimeMillis()
            val session = sessionManager.getSessionId()
            val baseResult = captureViewResult(baseWindowEntry) ?: return@withContext null

            // capture rest of views on top of base
            val pairs = mutableListOf<CaptureResult>()
            var afterBase = false
            for (windowEntry in windowsEntries) {
                if (afterBase) {
                    captureViewResult(windowEntry)?.let { result ->
                        pairs.add(result)
                    }
                } else if (windowEntry === baseWindowEntry) {
                    afterBase = true
                }
            }

            if (pairs.isNotEmpty() || baseResult.masks.isNotEmpty()) {
                suspendCancellableCoroutine { continuation ->
                    CoroutineScope(DispatcherProviderHolder.current.default).launch {
                        val canvas = Canvas(baseResult.bitmap)
                        drawMasks(canvas, baseResult.masks)

                        for (res in pairs) {
                            val entry = res.windowEntry
                            val dx = (entry.screenLeft - baseWindowEntry.screenLeft).toFloat()
                            val dy = (entry.screenTop - baseWindowEntry.screenTop).toFloat()

                            canvas.withTranslation(dx, dy) {
                                drawBitmap(res.bitmap, 0f, 0f, null)
                                drawMasks(canvas, res.masks)
                            }
                        }

                        continuation.resume(
                            createCaptureEvent(
                                baseResult.bitmap,
                                rect,
                                timestamp,
                                session
                            )
                        )
                    }
                }
            }

            return@withContext createCaptureEvent(baseResult.bitmap, rect, timestamp, session)
        }

    private fun pickBaseWindow(windowsEntries: List<WindowEntry>): WindowEntry? {
        windowsEntries.firstOrNull {
            (it.wmType == TYPE_APPLICATION || it.wmType == TYPE_BASE_APPLICATION)
        }?.let { return it }

        windowsEntries.firstOrNull { it.type == WindowType.ACTIVITY }?.let { return it }

        windowsEntries.firstOrNull { it.type == WindowType.DIALOG }?.let { return it }

        // Fallback to the first available
        return windowsEntries.firstOrNull()
    }

    private suspend fun captureViewResult(windowEntry: WindowEntry): CaptureResult? {
        val bitmap = captureViewBitmap(windowEntry) ?: return null
        val sensitiveComposeRects = sensitiveAreasCollector.collectFromActivity(windowEntry.rootView, maskMatchers)
        return CaptureResult(windowEntry, bitmap, sensitiveComposeRects)
    }

    private suspend fun captureViewBitmap(windowEntry: WindowEntry): Bitmap? {
        val view = windowEntry.rootView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val window = windowInspector.findWindow(view)
            if (window != null) {
                pixelCopy(window, view, windowEntry.rect())?.let { return it }
            }
        }

        // Fallback if window not available or old version
        return withContext(Dispatchers.Main.immediate) {
            if (!view.isAttachedToWindow || !view.isShown) return@withContext null

            return@withContext canvasDraw(view, windowEntry.rect())
        }
    }

    private suspend fun pixelCopy(
        window: Window,
        view: View,
        rect: Rect,
    ): Bitmap? {
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )

        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            PixelCopy.request(
                window,
                rect,
                bitmap,
                { result ->
                    if (!continuation.isActive) return@request
                    if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap)
                    } else {
                        continuation.resume(null)
                    }
                }, handler
            )
        }
    }

    private fun canvasDraw(
        view: View,
        rect: Rect,
    ): Bitmap? {
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun createCaptureEvent(
        postMask: Bitmap,
        rect: Rect,
        timestamp: Long,
        session: String
    ): CaptureEvent {
        // TODO: O11Y-625 - optimize memory allocations here, re-use byte arrays and such
        val outputStream = ByteArrayOutputStream()
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

        val captureEvent = CaptureEvent(
            imageBase64 = compressedImage,
            origWidth = rect.right,
            origHeight = rect.bottom,
            timestamp = timestamp,
            session = session
        )
        return captureEvent
    }

    /**
     * Applies masking rectangles to the provided [canvas] using the provided [masks].
     *
     * @param canvas The canvas to mask
     * @param masks rects that will be masked
     */
    private fun drawMasks(canvas: Canvas, masks: List<ComposeRect>) {
        val paint = Paint().apply {
            color = Color.GRAY
            style = Paint.Style.FILL
        }

        masks.forEach { rect ->
            val rect = Rect(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
            canvas.drawRect(rect, paint)
        }
    }
}
