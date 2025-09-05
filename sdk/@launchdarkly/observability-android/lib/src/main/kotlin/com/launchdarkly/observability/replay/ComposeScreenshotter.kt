package com.launchdarkly.observability.replay

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Screenshot(
    val bitmap: Bitmap,
    val timestamp: Long,
    val session: String
)

class ComposeScreenshotter(private val sessionManager: SessionManager): Application.ActivityLifecycleCallbacks {

    private var _activity: Activity? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val _screenshotFlow = MutableSharedFlow<Screenshot>()
    val screenshotFlow: SharedFlow<Screenshot> = _screenshotFlow.asSharedFlow()

    fun start() {
        // Start capturing screenshots periodically or on demand
    }

    fun pause() {
        // Pause screenshot capture
    }

    fun captureScreenshotNow() {
        coroutineScope.launch {
            val bitmap = captureScreenshot()
            if (bitmap != null) {
                _screenshotFlow.emit(Screenshot(bitmap, System.currentTimeMillis(), sessionManager.getSessionId()))
            }
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
        _activity = null;
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

    private suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.Main){
        val activity = _activity ?: return@withContext null;

        try {
            val window = activity.window
            val view = window.decorView

            // Get the window dimensions
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val width = rect.width()
            val height = rect.height()

            // Create a bitmap with the window dimensions
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Use PixelCopy to capture the window content
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                suspendCancellableCoroutine { continuation ->
                    PixelCopy.request(
                        window,
                        rect, // Use the whole window rect
                        bitmap,
                        { copyResult ->
                            if (copyResult == PixelCopy.SUCCESS) {
                                continuation.resume(bitmap)
                            } else {
                                continuation.resumeWithException(Exception("PixelCopy failed with result: $copyResult"))
                            }
                        },
                        Handler(Looper.getMainLooper()) // Handler for main thread
                    )
                }
            } else {
                throw NotImplementedError("Need to handle unsupported SDK versions")
            }
        } catch (e: Exception) {
            // fail loudly during development
            throw RuntimeException(e);
        }
    }
}