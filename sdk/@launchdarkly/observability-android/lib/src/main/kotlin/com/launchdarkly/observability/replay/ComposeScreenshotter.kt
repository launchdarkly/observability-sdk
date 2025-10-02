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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.getOrNull
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
            val decorView = window.decorView
            val width = decorView.width
            val height = decorView.height

            val rect = Rect(0, 0, width, height)

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

        // Find sensitive areas using Compose semantics
        val sensitiveComposeRects = findSensitiveComposeAreasFromActivity(activity)

        // Mask sensitive Compose areas found via semantics
        sensitiveComposeRects.forEach { composeRect ->
            val rect = Rect(
                composeRect.left.toInt(),
                composeRect.top.toInt(),
                composeRect.right.toInt(),
                composeRect.bottom.toInt()
            )
            canvas.drawRect(rect, paint)
        }

        return maskedBitmap
    }

    /**
     * Find sensitive Compose areas from all ComposeViews in the activity.
     */
    private fun findSensitiveComposeAreasFromActivity(activity: Activity): List<ComposeRect> {
        val allSensitiveRects = mutableListOf<ComposeRect>()
        
        try {
            // Find all ComposeViews in the activity
            val composeViews = findComposeViews(activity.window.decorView)
            
            // Process each ComposeView to find sensitive areas
            composeViews.forEach { composeView ->
                val semanticsOwner = getSemanticsOwner(composeView)
                val rootSemanticsNode = semanticsOwner?.rootSemanticsNode
                if (rootSemanticsNode != null) {
                    val sensitiveRects = findSensitiveComposeAreas(rootSemanticsNode, composeView)
                    allSensitiveRects.addAll(sensitiveRects)
                }
            }
        } catch (e: Exception) {
            // Handle cases where ComposeView access fails
        }
        
        return allSensitiveRects
    }

    /**
     * Recursively find all ComposeViews in the view hierarchy.
     */
    private fun findComposeViews(view: View): List<ComposeView> {
        val composeViews = mutableListOf<ComposeView>()
        
        if (view is ComposeView) {
            composeViews.add(view)
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                composeViews.addAll(findComposeViews(child))
            }
        }
        
        return composeViews
    }

    /**
     * Get the SemanticsOwner from a ComposeView using reflection.
     * This is necessary because AndroidComposeView and semanticsOwner are not publicly exposed.
     */
    private fun getSemanticsOwner(composeView: ComposeView): SemanticsOwner? {
        return try {
            // ComposeView contains an AndroidComposeView which has the semanticsOwner
            if (composeView.childCount > 0) {
                val androidComposeView = composeView.getChildAt(0)
                
                // Use reflection to check if this is an AndroidComposeView
                val androidComposeViewClass = Class.forName("androidx.compose.ui.platform.AndroidComposeView")
                if (androidComposeViewClass.isInstance(androidComposeView)) {
                    // Use reflection to access the semanticsOwner field
                    val field = androidComposeViewClass.getDeclaredField("semanticsOwner")
                    field.isAccessible = true
                    field.get(androidComposeView) as? SemanticsOwner
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Find sensitive Compose areas by traversing the semantic node tree.
     * Takes the root semantic node from the root view and recursively searches for sensitive content.
     */
    private fun findSensitiveComposeAreas(rootSemanticsNode: SemanticsNode, composeView: ComposeView): List<ComposeRect> {
        val sensitiveRects = mutableListOf<ComposeRect>()
        
        try {
            // Recursively traverse the semantic node tree to find sensitive areas
            traverseSemanticNode(rootSemanticsNode, sensitiveRects, composeView)
            
        } catch (e: Exception) {
            // Handle cases where semantic node traversal fails
            // This could happen if the semantic tree is not available or corrupted
        }
        
        return sensitiveRects
    }

    /**
     * Recursively traverse a semantic node and its children to find sensitive areas.
     */
    private fun traverseSemanticNode(node: SemanticsNode, sensitiveRects: MutableList<ComposeRect>, composeView: ComposeView) {
        // Check if this node is marked as sensitive
        if (isSensitiveNode(node)) {
            // Convert bounds to absolute screen coordinates
            val boundsInWindow = node.boundsInWindow
            val absoluteRect = ComposeRect(
                left = boundsInWindow.left,
                top = boundsInWindow.top,
                right = boundsInWindow.right,
                bottom = boundsInWindow.bottom
            )
            sensitiveRects.add(absoluteRect)
        }
        
        // Recursively traverse all children
        node.children.forEach { child ->
            traverseSemanticNode(child, sensitiveRects, composeView)
        }
    }

    /**
     * Check if a semantic node contains sensitive content based on test tags or content descriptions.
     */
    private fun isSensitiveNode(node: SemanticsNode): Boolean {
        // Check for test tag "ld-sensitive-mask"
        val testTag = node.config.getOrNull(SemanticsProperties.TestTag)
        if (testTag == "ld-sensitive-mask") {
            return true
        }
        
        // Check for content description containing "sensitive"
        val contentDescriptions = node.config.getOrNull(SemanticsProperties.ContentDescription)
        if (contentDescriptions?.any { it.contains("sensitive", ignoreCase = true) } == true) {
            return true
        }
        
        return false
    }
}