package com.launchdarkly.observability.replay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.observability.replay.capture.CaptureSource
import com.launchdarkly.observability.replay.capture.WindowInspector
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability.replay"

/*
TODO: O11Y-625 - determine where these should be defined ultimately and tune accordingly. Perhaps
 we don't need a batching exporter in this layer. Perhaps this layer shouldn't be the one
 that decides the parameters of the batching exporter. Perhaps the batching should be controlled by the instrumentation manager.
 */
private const val BATCH_MAX_QUEUE_SIZE = 100
private const val BATCH_SCHEDULE_DELAY_MS = 1000L
private const val BATCH_EXPORTER_TIMEOUT_MS = 5000L
private const val BATCH_MAX_EXPORT_SIZE = 10

/**
 * Provides session replay instrumentation. Session replays that are sampled will appear on the LaunchDarkly dashboard.
 *
 * @param options Configuration options for replay behavior including privacy settings and capture interval
 * @param observabilityContext Shared context provided by the Observability plugin
 *
 * @sample
 * ```kotlin
 * val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
 *     .mobileKey("your-mobile-key")
 *     .plugins(
 *         Components.plugins().setPlugins(
 *             listOf(
 *                 Observability(this@MyApplication, "your-mobile-key"),
 *                 SessionReplay(
 *                     ReplayOptions(
 *                         privacyProfile = PrivacyProfile.STRICT,
 *                     )
 *                 )
 *             )
 *         )
 *     )
 *     .build()
 * ```
 *
 * @see ReplayOptions for configuration options
 * @see PrivacyProfile for privacy settings
 */
class ReplayInstrumentation(
    private val options: ReplayOptions = ReplayOptions(),
    private val observabilityContext: ObservabilityContext
) : LDExtendedInstrumentation, Application.ActivityLifecycleCallbacks {

    private lateinit var otelLogger: Logger
    private var captureSource: CaptureSource? = null
    private var interactionSource: InteractionSource? = null
    private val instrumentationScope = CoroutineScope(DispatcherProviderHolder.current.default + SupervisorJob())
    private var captureJob: Job? = null
    private val shouldCapture = MutableStateFlow(false)
    private var startedActivityCount: Int = 0
    private var configurationChangeInProgress: Boolean = false
    private var isInstalled: Boolean = false

    override val name: String = INSTRUMENTATION_SCOPE_NAME

    override fun install(ctx: InstallationContext) {
        // If already installed, do nothing. This prevents duplicating collectors and lifecycle listeners.
        // We should refactor this if we want to support multiple sessions and install the instrumentation more than once
        if (isInstalled) return

        otelLogger = ctx.openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)
        captureSource = CaptureSource(
            sessionManager = ctx.sessionManager,
            maskMatchers = options.privacyProfile.asMatchersList(),
            logger = observabilityContext.logger
        )
        interactionSource = InteractionSource(ctx.sessionManager)

        startCollectors()
        startCaptureStateObserver()

        interactionSource?.attachToApplication(ctx.application)
        ctx.application.registerActivityLifecycleCallbacks(this)

        startCaptureIfInForeground(ctx.application)
        isInstalled = true
    }

    private fun startCollectors() {
        // Images collector
        instrumentationScope.launch {
            captureSource?.captureFlow?.collect { capture ->
                otelLogger.logRecordBuilder()
                    .setAttribute("event.domain", "media")
                    .setAttribute("image.width", capture.origWidth.toLong())
                    .setAttribute("image.height", capture.origHeight.toLong())
                    .setAttribute("image.data", capture.imageBase64)
                    .setAttribute("session.id", capture.session)
                    .setTimestamp(capture.timestamp, TimeUnit.MILLISECONDS)
                    .emit()
            }
        }

        // Interactions collector
        instrumentationScope.launch {
            interactionSource?.captureFlow?.collect { interaction ->
                val positionsJson = StringBuilder().apply {
                    append('[')
                    interaction.positions.forEachIndexed { index, position ->
                        if (index > 0) append(',')
                        append("{\"x\":").append(position.x)
                        append(",\"y\":").append(position.y)
                        append(",\"timestamp\":").append(position.timestamp)
                        append('}')
                    }
                    append(']')
                }.toString()

                val logTimestamp = interaction.positions.lastOrNull()?.timestamp ?: System.currentTimeMillis()

                otelLogger.logRecordBuilder()
                    .setAttribute("event.domain", "interaction")
                    .setAttribute("android.action", interaction.action)
                    .setAttribute("screen.coords", positionsJson)
                    .setAttribute("session.id", interaction.session)
                    .setTimestamp(logTimestamp, TimeUnit.MILLISECONDS)
                    .emit()
            }
        }
    }

    /**
     * Observes [shouldCapture] state changes and synchronizes the capture loop state.
     * Skips redundant operations if the desired state already matches the current state.
     */
    private fun startCaptureStateObserver() {
        instrumentationScope.launch {
            shouldCapture.collect { shouldRun ->
                val running = captureJob?.isActive == true
                if (shouldRun == running) return@collect
                if (shouldRun) doRunCapture() else doPauseCapture()
            }
        }
    }

    private suspend fun doRunCapture() {
        captureJob?.cancelAndJoin()
        captureJob = instrumentationScope.launch {
            observabilityContext.logger.debug("Session replay capture running")
            while (isActive) {
                try {
                    captureSource?.captureNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    observabilityContext.logger.error("Capture paused due to OOM", e)
                    shouldCapture.value = false
                    return@launch
                } catch (e: Exception) {
                    observabilityContext.logger.error("Capture failed", e)
                }
                delay(options.capturePeriodMillis)
            }
        }
    }

    private suspend fun doPauseCapture() {
        captureJob?.cancelAndJoin()
        captureJob = null
        observabilityContext.logger.debug("Session replay capture paused")
    }

    // TODO: O11Y-622 - implement mechanism for customer code to invoke this method
    fun runCapture() {
        shouldCapture.value = true
    }

    // TODO: O11Y-622 - implement mechanism for customer code to invoke this method
    fun pauseCapture() {
        shouldCapture.value = false
    }

    /**
     * Starts replay capture if the app is already in the foreground.
     *
     * This handles the scenario where the SDK is initialized after the application has already
     * started. Since [android.app.Application.ActivityLifecycleCallbacks] would have missed the
     * initial [onActivityStarted] events, this method manually checks for the presence of app
     * windows to determine if the app is in the foreground.
     *
     * If windows are found, this method sets `startedActivityCount` to 1 and calls [runCapture]
     * to begin replay capture, ensuring consistent behavior with normal foreground transitions.
     *
     * @param application The application instance used to inspect existing windows.
     */
    private fun startCaptureIfInForeground(application: Application) {
        if (startedActivityCount > 0) return

        instrumentationScope.launch(DispatcherProviderHolder.current.main) {
            if (startedActivityCount > 0) return@launch

            runCatching {
                if (WindowInspector(observabilityContext.logger).appWindows(application).isNotEmpty()) {
                    startedActivityCount = 1
                    runCapture()
                }
            }
        }
    }

    // TODO: O11Y-621 - This should be called somewhere (Probably inside InstrumentationManager.kt) to shutdown the instrumentation.
    fun shutdown() {
        pauseCapture()
        instrumentationScope.cancel()
        interactionSource?.detachFromApplication(observabilityContext.application)
        observabilityContext.application.unregisterActivityLifecycleCallbacks(this)
    }

    override fun getLoggerScopeName(): String = INSTRUMENTATION_SCOPE_NAME

    override fun getLogRecordProcessor(credential: String): LogRecordProcessor {
        val exporter = RRwebGraphQLReplayLogExporter(
            organizationVerboseId = credential, // The SDK credential is used as the organization ID intentionally
            backendUrl = observabilityContext.options.backendUrl,
            serviceName = observabilityContext.options.serviceName,
            serviceVersion = observabilityContext.options.serviceVersion,
        )

        return BatchLogRecordProcessor.builder(exporter)
            .setMaxQueueSize(BATCH_MAX_QUEUE_SIZE)
            .setScheduleDelay(BATCH_SCHEDULE_DELAY_MS, TimeUnit.MILLISECONDS)
            .setExporterTimeout(BATCH_EXPORTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(BATCH_MAX_EXPORT_SIZE)
            .build()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        val enteringForeground = startedActivityCount == 0

        if (configurationChangeInProgress) {
            configurationChangeInProgress = false
        }

        startedActivityCount++

        if (enteringForeground) {
            runCapture()
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        if (startedActivityCount > 0) {
            startedActivityCount--
        }

        if (activity.isChangingConfigurations) {
            configurationChangeInProgress = true
            return
        }

        if (startedActivityCount == 0) {
            pauseCapture()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
