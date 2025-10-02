package com.launchdarkly.observability.replay

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import android.graphics.SurfaceTexture
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
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

            // Try hardware-accelerated rendering first (API 29+)
            // This approach doesn't need global redaction state
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return@withContext captureWithHardwareRenderer(view, width, height)
            } else {
                // Fallback to software rendering for older APIs
                // Only the fallback needs global redaction state
                return@withContext captureWithSoftwareRendererWithRedaction(view, width, height)
            }
        } catch (e: Exception) {
            // fail loudly during development
            throw RuntimeException(e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun captureWithHardwareRenderer(view: View, width: Int, height: Int): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            try {
                // Create a bitmap to receive the rendered content
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                // Create a SurfaceTexture and Surface for hardware rendering
                val surfaceTexture = SurfaceTexture(0)
                surfaceTexture.setDefaultBufferSize(width, height)
                val surface = Surface(surfaceTexture)

                // Create and configure the hardware renderer
                val renderer = HardwareRenderer()
                val renderNode = RenderNode("screenshotNode")

                // Set up the render node with the view's content
                renderNode.setPosition(0, 0, width, height)
                val recordingCanvas = renderNode.beginRecording(width, height)

                // Force a layout pass to ensure all views are properly laid out
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, width, height)

                // Draw the view with selective masking applied in the isolated render context
                drawViewWithSelectiveMasking(view, recordingCanvas)
                renderNode.endRecording()

                // Set up the renderer
                renderer.setContentRoot(renderNode)
                renderer.setSurface(surface)

                // Render the frame synchronously
                renderer.createRenderRequest()
                    .setVsyncTime(System.nanoTime())
                    .syncAndDraw()

                // Use PixelCopy to copy the surface content to our bitmap
                PixelCopy.request(
                    surface,
                    bitmap,
                    { result ->
                        // Clean up resources
                        renderer.destroy()
                        surface.release()
                        surfaceTexture.release()

                        if (result == PixelCopy.SUCCESS) {
                            continuation.resume(bitmap)
                        } else {
                            continuation.resumeWithException(RuntimeException("PixelCopy failed with result: $result"))
                        }
                    },
                    Handler(Looper.getMainLooper())
                )

            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun captureWithSoftwareRenderer(view: View, width: Int, height: Int): Bitmap {
        // Create a bitmap with the window dimensions
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Force a layout pass to ensure all views are properly laid out
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        // Draw the root view to the canvas
        view.draw(canvas)

        return bitmap
    }
}