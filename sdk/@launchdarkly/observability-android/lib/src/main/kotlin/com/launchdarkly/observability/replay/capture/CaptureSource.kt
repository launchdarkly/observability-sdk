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
) :
    Application.ActivityLifecycleCallbacks {

    private var _mostRecentActivity: Activity? = null

    private val _captureEventFlow = MutableSharedFlow<CaptureEvent>()
    val captureFlow: SharedFlow<CaptureEvent> = _captureEventFlow.asSharedFlow()
    private val windowInspector = WindowInspector(logger)
    private val sensitiveAreasCollector = SensitiveAreasCollector(logger)

    /**
     * Attaches the [CaptureSource] to the [Application] whose [Activity]s will be captured.
     */
    fun attachToApplication(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Detaches the [CaptureSource] from the [Application].
     */
    fun detachFromApplication(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
    }

    /**
     * Requests a [CaptureEvent] be taken now.
     */
    suspend fun captureNow() {
        val capture = doCapture()
        if (capture != null) {
            _captureEventFlow.emit(capture)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Noop
    }

    override fun onActivityStarted(activity: Activity) {
        // Noop
    }

    override fun onActivityResumed(activity: Activity) {
        _mostRecentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {
        // this if check prevents pausing of a different activity from interfering with tracking of most recent activity.
        if (activity == _mostRecentActivity) {
            _mostRecentActivity = null
        }
    }

    override fun onActivityStopped(activity: Activity) {
        // Noop
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Noop
    }

    override fun onActivityDestroyed(activity: Activity) {
        // Noop
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

            val baseBitmap = captureViewBitmap(baseWindowEntry) ?: return@withContext null

            // capture rest of views on top of base
            val pairs = mutableListOf<Pair<WindowEntry, Bitmap>>()
            var afterBase = false
            for (windowEntry in windowsEntries) {
                if (afterBase) {
                    captureViewBitmap(windowEntry)?.let { bitmap ->
                        pairs.add(Pair(windowEntry, bitmap))
                    }
                } else if (windowEntry === baseWindowEntry) {
                    afterBase = true
                }
            }
            if (pairs.isEmpty()) {
                // No on top windows and we return baseBitmap
                return@withContext createCaptureEvent(baseBitmap, rect, timestamp, session)
            }

            val canvas = Canvas(baseBitmap)
            for ((entry, bitmap) in pairs) {
                val dx = (entry.screenLeft - baseWindowEntry.screenLeft).toFloat()
                val dy = (entry.screenTop - baseWindowEntry.screenTop).toFloat()

                canvas.withTranslation(dx, dy) {
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }

            return@withContext createCaptureEvent(baseBitmap, rect, timestamp, session)


//            } catch (e: Exception) {
//                // TODO: O11Y-624 - implement handling/shutdown for errors and unsupported API levels
//                throw RuntimeException(e)
//            }
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

    private suspend fun pixelCopyOld(
        window: Window,
        view: View,
        rect: Rect,
        bitmap: Bitmap
    ): CaptureEvent? {
        val sensitiveComposeRects =
            sensitiveAreasCollector.collectFromActivity(view, maskMatchers)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // TODO: O11Y-624 - implement handling/shutdown for errors and unsupported API levels
            throw NotImplementedError("CaptureSource does not work on unsupported Android SDK version")
        }

        // TODO: O11Y-624 - read PixelCopy exception recommendations and adjust logic to account for such cases
        try {
            PixelCopy.request(
                window,
                rect,
                bitmap,
                { result ->
                    val timestamp = System.currentTimeMillis()
                    val session = sessionManager.getSessionId()

                    if (result == PixelCopy.SUCCESS) {
                        CoroutineScope(DispatcherProviderHolder.current.default).launch {
                            try {
                                val postMask = if (sensitiveComposeRects.isNotEmpty()) {
                                    maskSensitiveRects(
                                        bitmap,
                                        sensitiveComposeRects
                                    )
                                } else {
                                    bitmap
                                }

                                createCaptureEvent(postMask, rect, timestamp, session)
                                //continuation.resume(captureEvent)
                            } catch (e: Exception) {
                                //continuation.resumeWithException(e)
                            }
                        }
                    } else {
                        // TODO: O11Y-624 - implement handling/shutdown for errors and unsupported API levels
//                        continuation.resumeWithException(
//                            Exception("PixelCopy failed with result: $result")
//                        )
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {

        }
        //}

        return null
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
     * Applies masking rectangles to the provided [bitmap] using the provided [sensitiveRects].
     *
     * @param bitmap The bitmap to mask
     * @param sensitiveRects rects that will be masked
     */
    private fun maskSensitiveRects(bitmap: Bitmap, sensitiveRects: List<ComposeRect>): Bitmap {
        if (sensitiveRects.isEmpty()) {
            return bitmap
        }

        // TODO: O11Y-625 - remove this bitmap copy if possible for memory optimization purposes
        val maskedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(maskedBitmap)
        val paint = Paint().apply {
            color = Color.GRAY
            style = Paint.Style.FILL
        }

        sensitiveRects.forEach { rect ->
            val rect = Rect(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
            canvas.drawRect(rect, paint)
        }

        return maskedBitmap
    }
}
