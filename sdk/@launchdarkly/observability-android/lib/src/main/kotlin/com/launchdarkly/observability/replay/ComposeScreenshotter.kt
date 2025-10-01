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
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
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
                                val maskedBitmap = maskSensitiveAreas(bitmap, activity)
                                continuation.resume(maskedBitmap)
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

    private fun maskSensitiveAreas(bitmap: Bitmap, activity: Activity): Bitmap {
        val maskedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(maskedBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        // Find sensitive view locations and mask them
        val sensitiveViews = findSensitiveComposeViews(activity)
        sensitiveViews.forEach { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)

            // Convert screen coordinates to bitmap coordinates
            val rect = Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height
            )

            canvas.drawRect(rect, paint)
        }

        return maskedBitmap
    }

    private fun findSensitiveComposeViews(activity: Activity): List<View> {
        val sensitiveViews = mutableListOf<View>()
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

        findSensitiveViewsRecursive(rootView, sensitiveViews)
        return sensitiveViews
    }

    private fun findSensitiveViewsRecursive(viewGroup: ViewGroup, sensitiveViews: MutableList<View>) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)

            // Check for custom sensitive markers
            if (child.tag == "sensitive" ||
                child.contentDescription?.contains("password", ignoreCase = true) == true ||
                child.contentDescription?.contains("sensitive", ignoreCase = true) == true ||
                child.tag == "ld-sensitive-mask") {
                sensitiveViews.add(child)
            }
            
            // Check for Compose views that might contain sensitive content
            // Compose views are typically wrapped in ComposeView or similar containers
            if (child.javaClass.simpleName.contains("ComposeView") || 
                child.javaClass.simpleName.contains("AndroidComposeView")) {
                // For Compose views, we need to check if they contain sensitive content
                // Since we can't access Compose semantics directly, we'll use a different approach
                // We'll check if the view has any children that might be sensitive
                checkComposeViewForSensitiveContent(child, sensitiveViews)
            }
            
            // Recursively check children
            if (child is ViewGroup) {
                findSensitiveViewsRecursive(child, sensitiveViews)
            }
        }
    }
    
    private fun checkComposeViewForSensitiveContent(view: View, sensitiveViews: MutableList<View>) {
        // For Compose views, we can't directly access the semantics, but we can
        // check if the view itself has been marked as sensitive through other means
        // or if it contains traditional Android views that are sensitive
        
        // Check if the Compose view itself has been marked as sensitive
        if (view.tag == "ld-sensitive-mask" || 
            view.contentDescription?.contains("sensitive", ignoreCase = true) == true) {
            sensitiveViews.add(view)
        }
        
        // If it's a ViewGroup, check its children for traditional Android views
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child.tag == "sensitive" ||
                    child.contentDescription?.contains("password", ignoreCase = true) == true ||
                    child.contentDescription?.contains("sensitive", ignoreCase = true) == true ||
                    child.tag == "ld-sensitive-mask") {
                    sensitiveViews.add(child)
                }
                
                // Recursively check children
                if (child is ViewGroup) {
                    checkComposeViewForSensitiveContent(child, sensitiveViews)
                }
            }
        }
    }
}