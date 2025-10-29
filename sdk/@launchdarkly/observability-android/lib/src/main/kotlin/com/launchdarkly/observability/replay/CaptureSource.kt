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
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
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
    private val maskMatchers: List<MaskMatcher>,
    // TODO: O11Y-628 - add captureQuality options
) :
    Application.ActivityLifecycleCallbacks {

    private var _activity: Activity? = null

    private val _captureFlow = MutableSharedFlow<Capture>()
    val captureFlow: SharedFlow<Capture> = _captureFlow.asSharedFlow()

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                suspendCancellableCoroutine { continuation ->

                    // Synchronize with UI rendering frame
                    Choreographer.getInstance().postFrameCallback {
                        val sensitiveComposeRects =
                            findSensitiveComposeAreasFromActivity(activity, maskMatchers)

                        // TODO: O11Y-624 - read PixelCopy exception recommendations and adjust logic to account for such cases
                        PixelCopy.request(
                            window,
                            rect,
                            bitmap,
                            { result ->
                                val timestamp = System.currentTimeMillis()
                                val session = sessionManager.getSessionId()

                                if (result == PixelCopy.SUCCESS) {
                                    CoroutineScope(Dispatchers.Default).launch {
                                        try {
                                            val postMask = if (maskMatchers.isNotEmpty()) {
                                                maskSensitiveAreas(bitmap, sensitiveComposeRects)
                                            } else {
                                                bitmap
                                            }

                                            // TODO: O11Y-625 - optimize memory allocations here, re-use byte arrays and such
                                            val outputStream = ByteArrayOutputStream()
                                            // TODO: O11Y-628 - calculate quality using captureQuality options
                                            postMask.compress(Bitmap.CompressFormat.WEBP, 30, outputStream)
                                            val byteArray = outputStream.toByteArray()
                                            val compressedImage = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                                            val capture = Capture(
                                                imageBase64 = compressedImage,
                                                origWidth = decorViewWidth,
                                                origHeight = decorViewHeight,
                                                timestamp = timestamp,
                                                session = session
                                            )
                                            continuation.resume(capture)
                                        } catch (e: Exception) {
                                            continuation.resumeWithException(e)
                                        }
                                    }
                                } else {
                                    // TODO: O11Y-624 - implement handling/shutdown for errors and unsupported API levels
                                    continuation.resumeWithException(
                                        Exception("PixelCopy failed with result: $result")
                                    )
                                }
                            },
                            Handler(Looper.getMainLooper())
                        )
                    }
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
     * Applies masking rectangles to the provided [bitmap] by inspecting the provided [activity] for
     * content that needs to be masked.
     *
     * @param bitmap The bitmap to mask
     * @param activity The activity that the bitmap was captured from.
     */
    private fun maskSensitiveAreas(bitmap: Bitmap, sensitiveRects: List<ComposeRect>): Bitmap {
        // TODO: O11Y-625 - remove this bitmap copy if possible for memory optimization purposes
        val maskedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(maskedBitmap)
        val paint = Paint().apply {
            color = Color.GRAY
            style = Paint.Style.FILL
        }

        // Mask sensitive Compose areas found via semantics
        sensitiveRects.forEach { composeRect ->
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
     *
     * @return a list of rects that represent sensitive areas that need to be masked
     */
    private fun findSensitiveComposeAreasFromActivity(activity: Activity, matchers: List<MaskMatcher>): List<ComposeRect> {
        val allSensitiveRects = mutableListOf<ComposeRect>()

        try {
            // Find all ComposeViews in the activity
            val composeViews = findComposeViews(activity.window.decorView)

            // Process each ComposeView to find sensitive areas
            composeViews.forEach { composeView ->
                val semanticsOwner = getSemanticsOwner(composeView)
                val rootSemanticsNode = semanticsOwner?.unmergedRootSemanticsNode
                if (rootSemanticsNode != null) {
                    val sensitiveRects = findSensitiveComposeAreas(rootSemanticsNode, composeView, matchers)
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
     *
     * @return list of compose views
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
     * Gets the SemanticsOwner from a ComposeView using reflection. This is necessary because
     * AndroidComposeView and semanticsOwner are not publicly exposed.
     */
    private fun getSemanticsOwner(composeView: ComposeView): SemanticsOwner? {
        return try {
            // ComposeView contains an AndroidComposeView which has the semanticsOwner
            if (composeView.childCount > 0) {
                val androidComposeView = composeView.getChildAt(0)

                // TODO: O11Y-620 - determine if there is a more robust long term way to achieve this, this reflection is fragile.
                // Use reflection to check if this is an AndroidComposeView
                val androidComposeViewClass =
                    Class.forName("androidx.compose.ui.platform.AndroidComposeView")
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
     */
    private fun findSensitiveComposeAreas(
        rootSemanticsNode: SemanticsNode,
        composeView: ComposeView,
        matchers: List<MaskMatcher>
    ): List<ComposeRect> {
        val sensitiveRects = mutableListOf<ComposeRect>()

        try {
            // Recursively traverse the semantic node tree to find sensitive areas
            traverseSemanticNode(rootSemanticsNode, sensitiveRects, composeView, matchers)

        } catch (e: Exception) {
            // This could happen if the semantic tree is not available or corrupted
        }

        return sensitiveRects
    }

    /**
     * Recursively traverse a semantic node and its children to find sensitive areas.
     */
    private fun traverseSemanticNode(
        node: SemanticsNode,
        sensitiveRects: MutableList<ComposeRect>,
        composeView: ComposeView,
        matchers: List<MaskMatcher>
    ) {
        for (matcher in matchers) {
            if (matcher.isMatch(node)) {
                val boundsInWindow = node.boundsInWindow
                val absoluteRect = ComposeRect(
                    left = boundsInWindow.left,
                    top = boundsInWindow.top,
                    right = boundsInWindow.right,
                    bottom = boundsInWindow.bottom
                )
                sensitiveRects.add(absoluteRect)
                break
            }
        }

        // Recursively traverse all children
        node.children.forEach { child ->
            traverseSemanticNode(child, sensitiveRects, composeView, matchers)
        }
    }
}
