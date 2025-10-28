package com.launchdarkly.observability.replay

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
import kotlin.coroutines.resumeWithException
import androidx.compose.ui.geometry.Rect as ComposeRect

/**
 * A source of [Capture]s taken from the most recently resumed [Activity]s window. Captures
 * are emitted on the [captureFlow] property of this class.
 *
 * @param sessionManager Used to get current session for tagging [Capture] with session id
 */
class CaptureSource(
    private val sessionManager: SessionManager,
    private val privacyProfile: PrivacyProfile,
    // TODO: O11Y-628 - add captureQuality options
) : Application.ActivityLifecycleCallbacks {

    private var _activity: Activity? = null
    private val maskRegistry = PrivacyMaskRegistry()

    private val _captureFlow = MutableSharedFlow<Capture>()
    val captureFlow: SharedFlow<Capture> = _captureFlow.asSharedFlow()

    /**
     * Attaches the [CaptureSource] to the [Application] whose [Activity]s will be captured.
     */
    fun attachToApplication(application: Application) {
        PrivacyMasking.register(maskRegistry)
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Detaches the [CaptureSource] from the [Application].
     */
    fun detachFromApplication(application: Application) {
        PrivacyMasking.unregister(maskRegistry)
        application.unregisterActivityLifecycleCallbacks(this)
    }

    /**
     * Requests a [Capture] be taken now.
     */
    suspend fun captureNow() {
        val capture = doCapture()
        if (capture != null) {
            _captureFlow.emit(capture)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Noop
    }

    override fun onActivityStarted(activity: Activity) {
        // Noop
    }

    override fun onActivityResumed(activity: Activity) {
        _activity = activity
    }

    override fun onActivityPaused(activity: Activity) {
        _activity = null
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
    private suspend fun doCapture(): Capture? = withContext(Dispatchers.Main) {
        val activity = _activity ?: return@withContext null

        try {
            val window = activity.window
            val decorView = window.decorView
            val decorViewWidth = decorView.width
            val decorViewHeight = decorView.height

            val rect = Rect(0, 0, decorViewWidth, decorViewHeight)

            // protect against race condition where decor view has no size
            if (decorViewWidth <= 0 || decorViewHeight <= 0) {
                return@withContext null
            }

            // TODO: O11Y-625 - optimize memory allocations
            // TODO: O11Y-625 - see if holding bitmap is more efficient than base64 encoding immediately after compression
            // TODO: O11Y-628 - use captureQuality option for scaling and adjust this bitmap accordingly, may need to investigate power of 2 rounding for performance
            // Create a bitmap with the window dimensions
            val bitmap = Bitmap.createBitmap(decorViewWidth, decorViewHeight, Bitmap.Config.ARGB_8888)

            // Use PixelCopy to capture the window content
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                suspendCancellableCoroutine { continuation ->
                    // TODO: O11Y-624 - read PixelCopy exception recommendations and adjust logic to account for such cases
                    PixelCopy.request(
                        window,
                        rect,
                        bitmap,
                        { result ->
                            // record attributes immediately to provide accurate stamping
                            val timestamp = System.currentTimeMillis()
                            val session = sessionManager.getSessionId()

                            if (result == PixelCopy.SUCCESS) {
                                // Offload heavy bitmap work to a background dispatcher
                                CoroutineScope(Dispatchers.Default).launch {
                                    try {
                                        // TODO: O11Y-620 - masking
                                        val postMask: Bitmap =
                                            if (privacyProfile == PrivacyProfile.STRICT) {
                                                val maskRects = maskRegistry.snapshot()
                                                maskSensitiveAreas(bitmap, maskRects)
                                            } else {
                                                bitmap
                                            }

                                        // TODO: O11Y-625 - optimize memory allocations here, re-use byte arrays and such
                                        val outputStream = ByteArrayOutputStream()
                                        // TODO: O11Y-628 - calculate quality using captureQuality options
                                        postMask.compress(Bitmap.CompressFormat.WEBP, 30, outputStream)
                                        val byteArray = outputStream.toByteArray()
                                        val compressedImage =
                                            Base64.encodeToString(byteArray, Base64.NO_WRAP)

                                        val capture = Capture(
                                            imageBase64 = compressedImage,
                                            origWidth = decorViewWidth,
                                            origHeight = decorViewHeight,
                                            timestamp = timestamp,
                                            session = session,
                                        )
                                        continuation.resume(capture)
                                    } catch (e: Exception) {
                                        continuation.resumeWithException(e)
                                    }
                                }
                            } else {
                                // TODO: O11Y-624 - implement handling/shutdown for errors and unsupported API levels
                                continuation.resumeWithException(Exception("PixelCopy failed with result: $result"))
                            }
                        },
                        Handler(Looper.getMainLooper()) // Handler for main thread
                    )
                }
            } else {
                // TODO: O11Y-624 - implement handling/shutdown for errors and unsupported API levels
                throw NotImplementedError("CaptureSource does not work on unsupported Android SDK version")
            }
        } catch (e: Exception) {
            // TODO: O11Y-624 - implement handling/shutdown for errors and unsupported API levels
            throw RuntimeException(e)
        }
    }

    /**
     * Applies masking rectangles to the provided [bitmap].
     *
     * @param bitmap The bitmap to mask.
     * @param maskRects Rectangles (window coordinates) that should be painted over.
     */
    private fun maskSensitiveAreas(
        bitmap: Bitmap,
        maskRects: List<ComposeRect>
    ): Bitmap {
        if (maskRects.isEmpty()) {
            return bitmap
        }
        // TODO: O11Y-625 - remove this bitmap copy if possible for memory optimization purposes
        val maskedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(maskedBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        maskRects.forEach { composeRect ->
            canvas.drawRect(
                composeRect.left,
                composeRect.top,
                composeRect.right,
                composeRect.bottom,
                paint
            )
        }

        return maskedBitmap
    }
}
