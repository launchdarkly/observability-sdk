package com.launchdarkly.observability.client

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver.OnDrawListener
import androidx.activity.FullyDrawnReporterOwner
import com.launchdarkly.observability.interfaces.Metric
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.api.common.Attributes
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability.launchtime"

/**
 * Tracks launch performance metrics for Activities. It records both:
 *  - Time to initial display (TTID): first frame rendered.
 *  - Time to full display (TTFD): UI considered ready for interaction.
 *
 * Classification by launch type (cold, warm, hot) and launch duration (TTID, TTFD) follow Android vitals definitions.
 * https://developer.android.com/topic/performance/vitals/launch-time
 */
internal class LaunchTimeInstrumentation(
    private val application: Application,
    private val metricRecorder: (Metric) -> Unit
) : AndroidInstrumentation, Application.ActivityLifecycleCallbacks {

    private val createdActivities = HashSet<Activity>()
    private val activityCreationTimestampsNs = WeakHashMap<Activity, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var startedActivityCount = 0
    private var configurationChangeInProgress = false
    private var isColdStart = true
    private var currentSession: LaunchSession? = null


    override val name: String = INSTRUMENTATION_SCOPE_NAME

    override fun install(ctx: InstallationContext) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        createdActivities.add(activity)
        activityCreationTimestampsNs[activity] = SystemClock.elapsedRealtimeNanos()
    }

    override fun onActivityStarted(activity: Activity) {
        val wasInBackground = startedActivityCount == 0 && !configurationChangeInProgress

        if (wasInBackground) {
            beginSession(activity)
        } else if (configurationChangeInProgress) {
            configurationChangeInProgress = false
        }

        createdActivities.remove(activity)
        startedActivityCount++
    }

    override fun onActivityResumed(activity: Activity) {
        currentSession?.takeIf { it.activityReference.get() == activity }?.let { session ->
            scheduleFirstDrawFallback(session)
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityStopped(activity: Activity) {
        if (startedActivityCount > 0) {
            startedActivityCount--
        }

        if (activity.isChangingConfigurations) {
            configurationChangeInProgress = true
            return
        }

        if (startedActivityCount == 0) {
            currentSession?.let { cancelSession(it) }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        createdActivities.remove(activity)
        activityCreationTimestampsNs.remove(activity)

        currentSession?.takeIf { it.activityReference.get() == activity }?.let {
            cancelSession(it)
        }
    }

    fun shutdown() {
        application.unregisterActivityLifecycleCallbacks(this)
        currentSession?.let { cancelSession(it) }
    }

    private fun beginSession(activity: Activity) {
        val type = resolveLaunchType(activity)
        currentSession?.let { cancelSession(it) }

        val session = LaunchSession(
            activityReference = WeakReference(activity),
            launchType = type,
            startUptimeNs = resolveStartTimestampNs(activity, type)
        )

        currentSession = session
        registerFirstDrawListener(session)
        registerFullyDrawnListener(session)
        isColdStart = false
    }

    private fun resolveStartTimestampNs(activity: Activity, type: LaunchType): Long {
        return when (type) {
            LaunchType.COLD, LaunchType.WARM -> {
                activityCreationTimestampsNs.remove(activity) ?: SystemClock.elapsedRealtimeNanos()
            }

            LaunchType.HOT -> SystemClock.elapsedRealtimeNanos()
        }
    }

    private fun resolveLaunchType(activity: Activity): LaunchType {
        return when {
            isColdStart -> LaunchType.COLD
            createdActivities.contains(activity) -> LaunchType.WARM
            else -> LaunchType.HOT
        }
    }

    private fun registerFirstDrawListener(session: LaunchSession) {
        if (session.onDrawListener != null || session.isCancelled.get()) {
            return
        }

        val activity = session.activityReference.get() ?: return
        val decorView = activity.window?.decorView

        if (decorView == null) {
            mainHandler.post { registerFirstDrawListener(session) }
            return
        }

        val observer = decorView.viewTreeObserver
        if (!observer.isAlive) {
            mainHandler.post { registerFirstDrawListener(session) }
            return
        }

        val listener = object : OnDrawListener {
            override fun onDraw() {
                if (session.isCancelled.get()) {
                    decorView.post { session.removeOnDrawListener() }
                    return
                }

                decorView.post {
                    val currentObserver = decorView.viewTreeObserver
                    val targetObserver = if (observer.isAlive) observer else currentObserver
                    if (targetObserver.isAlive) {
                        targetObserver.removeOnDrawListener(this)
                    }
                }

                onFirstDraw(session)
            }
        }

        session.decorViewRef = WeakReference(decorView)
        session.onDrawListener = listener
        observer.addOnDrawListener(listener)
    }

    private fun registerFullyDrawnListener(session: LaunchSession) {
        val activity = session.activityReference.get() ?: return
        val owner = activity as? FullyDrawnReporterOwner ?: return

        val listener: () -> Unit = {
            mainHandler.post { onFullyDrawn(session) }
            Unit
        }

        owner.fullyDrawnReporter.addOnReportDrawnListener(listener)
        session.fullyDrawnReporterOwner = WeakReference(owner)
        session.fullyDrawnReporterListener = listener
    }


    /**
     * Schedules a fallback to record the initial display time (TTID).
     *
     * This method posts a task to be run on the main thread. If the primary onDraw listener
     * has not yet recorded the first draw time by the time this task executes, this fallback
     * will do so to ensure the TTID metric is always captured.
     */
    private fun scheduleFirstDrawFallback(session: LaunchSession) {
        mainHandler.post {
            if (session.shouldTriggerFirstDrawFallback()) {
                session.removeOnDrawListener()
                onFirstDraw(session)
            }
        }
    }

    private fun onFirstDraw(session: LaunchSession) {
        if (session.isCancelled.get()) {
            return
        }

        if (!session.isFirstDrawRecorded.compareAndSet(false, true)) {
            return
        }

        val durationNs = SystemClock.elapsedRealtimeNanos() - session.startUptimeNs
        recordDurationMetric(LAUNCH_TTID_METRIC_NAME, durationNs, session)
        startTtfdTracking(session)
    }


    /**
     * Starts tracking the Time to Fully Drawn (TTFD).
     *
     * This function is called after the initial draw (TTID) has been recorded. Its goal is to
     * determine when the activity's UI has become "stable," which is considered the point when it
     * is fully loaded and ready for user interaction.
     *
     * To achieve this, it attaches a [Choreographer.FrameCallback] that runs on every frame.
     * The UI is considered stable after a certain number of consecutive frames
     * [TTFD_STABLE_FRAME_THRESHOLD] have occurred where the activity has window focus, is shown,
     * and no layout has been requested.
     *
     * It also sets a timeout as a fallback to record the metric if the stability condition
     * is not met within a reasonable time.
     *
     * @param session The current launch session being measured.
     */
    private fun startTtfdTracking(session: LaunchSession) {
        if (session.isCancelled.get()) {
            return
        }

        if (!session.ttfdTrackingStarted.compareAndSet(false, true)) {
            return
        }

        val activity = session.activityReference.get()
        val decor = session.decorViewRef.get()

        if (activity == null || decor == null) {
            onFullyDrawn(session)
            return
        }

        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (session.isCancelled.get() || session.isTtfdRecorded.get()) {
                    return
                }

                val currentActivity = session.activityReference.get()
                val currentDecor = session.decorViewRef.get()

                if (currentActivity == null || currentDecor == null) {
                    session.cancelTtfdTracking(mainHandler)
                    return
                }

                val hasFocus = currentActivity.hasWindowFocus()
                val isStable = hasFocus && currentDecor.isShown && !currentDecor.isLayoutRequested

                session.stableFrameCount = if (isStable) session.stableFrameCount + 1 else 0

                if (session.stableFrameCount >= TTFD_STABLE_FRAME_THRESHOLD) {
                    onFullyDrawn(session)
                    return
                }

                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        session.ttfdFrameCallback = frameCallback
        Choreographer.getInstance().postFrameCallback(frameCallback)

        val timeoutRunnable = Runnable {
            if (!session.isCancelled.get() && !session.isTtfdRecorded.get()) {
                onFullyDrawn(session)
            }
        }

        session.ttfdTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, TTFD_FALLBACK_TIMEOUT_MS)
    }

    private fun onFullyDrawn(session: LaunchSession) {
        if (session.isCancelled.get()) {
            return
        }

        if (!session.isTtfdRecorded.compareAndSet(false, true)) {
            return
        }

        val durationNs = SystemClock.elapsedRealtimeNanos() - session.startUptimeNs
        recordDurationMetric(LAUNCH_TTFD_METRIC_NAME, durationNs, session)

        cancelSession(session)
    }

    private fun recordDurationMetric(
        metricName: String,
        durationNs: Long,
        session: LaunchSession
    ) {
        val activity = session.activityReference.get() ?: return

        val durationMs = durationNs.toDouble() / NANOS_PER_MILLISECOND

        val attributes = Attributes.builder()
            .put(LAUNCH_TYPE_ATTRIBUTE, session.launchType.metricValue)
            .put(LAUNCH_ACTIVITY_ATTRIBUTE, activity.javaClass.name)
            .build()

        metricRecorder(
            Metric(
                name = metricName,
                value = durationMs,
                attributes = attributes
            )
        )
    }

    private fun cancelSession(session: LaunchSession) {
        session.isCancelled.set(true)
        session.cleanup(mainHandler)

        if (currentSession === session) {
            currentSession = null
        }
    }

    private class LaunchSession(
        val activityReference: WeakReference<Activity>,
        val launchType: LaunchType,
        val startUptimeNs: Long,
        var decorViewRef: WeakReference<View?> = WeakReference<View?>(null),
        var onDrawListener: OnDrawListener? = null,
        var fullyDrawnReporterOwner: WeakReference<FullyDrawnReporterOwner>? = null,
        var fullyDrawnReporterListener: (() -> Unit)? = null,
        var ttfdFrameCallback: Choreographer.FrameCallback? = null,
        var ttfdTimeoutRunnable: Runnable? = null,
        val isFirstDrawRecorded: AtomicBoolean = AtomicBoolean(false),
        val isTtfdRecorded: AtomicBoolean = AtomicBoolean(false),
        val ttfdTrackingStarted: AtomicBoolean = AtomicBoolean(false),
        val isCancelled: AtomicBoolean = AtomicBoolean(false),
        val isFallbackTriggered: AtomicBoolean = AtomicBoolean(false),
        var stableFrameCount: Int = 0
    ) {

        /**
         * Atomically checks if the first-draw fallback should run.
         *
         * This ensures the fallback logic is attempted only once.
         *
         * @return `true` if the fallback should be executed, `false` otherwise.
         */
        fun shouldTriggerFirstDrawFallback(): Boolean {
            if (isFallbackTriggered.compareAndSet(false, true)) {
                return !isCancelled.get() && !isFirstDrawRecorded.get()
            }
            return false
        }

        fun removeOnDrawListener() {
            val decor = decorViewRef.get()

            if (decor != null && onDrawListener != null) {
                val observer = decor.viewTreeObserver
                if (observer.isAlive) {
                    observer.removeOnDrawListener(onDrawListener)
                }
            }

            onDrawListener = null
        }

        fun removeFullyDrawnListener() {
            val owner = fullyDrawnReporterOwner?.get()
            val listener = fullyDrawnReporterListener

            if (owner != null && listener != null) {
                runCatching { owner.fullyDrawnReporter.removeOnReportDrawnListener(listener) }
            }

            fullyDrawnReporterListener = null
            fullyDrawnReporterOwner = null
        }

        fun cancelTtfdTracking(mainHandler: Handler) {
            ttfdFrameCallback?.let {
                Choreographer.getInstance().removeFrameCallback(it)
            }
            ttfdFrameCallback = null

            ttfdTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            ttfdTimeoutRunnable = null

            stableFrameCount = 0
            ttfdTrackingStarted.set(false)
        }

        fun cleanup(mainHandler: Handler) {
            removeOnDrawListener()
            removeFullyDrawnListener()
            cancelTtfdTracking(mainHandler)
            decorViewRef.clear()
        }
    }

    private enum class LaunchType(val metricValue: String) {
        COLD("cold"),
        WARM("warm"),
        HOT("hot")
    }

    private companion object {
        private const val LAUNCH_TTID_METRIC_NAME = "app.launch.duration.ttid"
        private const val LAUNCH_TTFD_METRIC_NAME = "app.launch.duration.ttfd"
        private const val LAUNCH_TYPE_ATTRIBUTE = "launch.type"
        private const val LAUNCH_ACTIVITY_ATTRIBUTE = "launch.activity"
        private const val TTFD_STABLE_FRAME_THRESHOLD = 2
        private const val TTFD_FALLBACK_TIMEOUT_MS = 10_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}
