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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private lateinit var captureSource: CaptureSource
    private lateinit var interactionSource: InteractionSource
    private val instrumentationScope = CoroutineScope(DispatcherProviderHolder.current.default + SupervisorJob())
    private var captureJob: Job? = null
    private var isPaused: Boolean = false
    private val captureMutex = Mutex()
    private var startedActivityCount: Int = 0
    private var configurationChangeInProgress: Boolean = false
    private var isInstalled: Boolean = false

    override val name: String = INSTRUMENTATION_SCOPE_NAME

    override fun install(ctx: InstallationContext) {
        // If already installed, do nothing. This prevents duplicating collectors and lifecycle listeners.
        if (isInstalled) return

        otelLogger = ctx.openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)
        captureSource = CaptureSource(
            sessionManager = ctx.sessionManager,
            maskMatchers = options.privacyProfile.asMatchersList(),
            logger = observabilityContext.logger
        )
        interactionSource = InteractionSource(ctx.sessionManager)

        startCollectors()

        interactionSource.attachToApplication(ctx.application)

        ctx.application.registerActivityLifecycleCallbacks(this)
        determineInitialForegroundState(ctx.application)

        isInstalled = true
        internalStartCapture()
    }

    private fun startCollectors() {
        // Images collector
        instrumentationScope.launch {
            captureSource.captureFlow.collect { capture ->
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
            interactionSource.captureFlow.collect { interaction ->
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

    // TODO: O11Y-622 - implement mechanism for customer code to invoke this method
    suspend fun runCapture() {
        captureMutex.withLock {
            if (!isPaused) return
            isPaused = false
            internalStartCapture()
        }
    }

    // TODO: O11Y-622 - implement mechanism for customer code to invoke this method
    suspend fun pauseCapture() {
        captureMutex.withLock {
            if (isPaused) return
            isPaused = true
            captureJob?.cancelAndJoin()
            captureJob = null
            observabilityContext.logger.debug("Session replay capture paused")
        }
    }

    private fun internalStartCapture() {
        captureJob?.cancel()
        captureJob = instrumentationScope.launch {
            try {
                observabilityContext.logger.debug("Session replay capture running")
                while (isActive) {
                    captureSource.captureNow()
                    delay(options.capturePeriodMillis)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Determines the initial foreground/background state.
     *
     * This is necessary to handle scenarios where the SDK is initialized after the application
     * has already started. Since the [android.app.Application.ActivityLifecycleCallbacks] would have
     * missed the initial [onActivityStarted] events, this method manually checks for the presence
     * of app windows to correctly set the internal state.
     *
     * This method checks if there are any activities running, and if so, it sets the
     * `startedActivityCount` to 1. This ensures that the replay capture will start if the app
     * is already in the foreground when the instrumentation is installed.
     *
     * @param application The application instance used to inspect existing windows.
     */
    private fun determineInitialForegroundState(application: Application) {
        if (startedActivityCount > 0) return

        instrumentationScope.launch(DispatcherProviderHolder.current.main) {
            if (startedActivityCount > 0) return@launch

            runCatching {
                if (WindowInspector(observabilityContext.logger).appWindows(application).isNotEmpty()) {
                    startedActivityCount = 1
                }
            }
        }
    }

    // TODO: O11Y-621 - This should be called somewhere (Probably inside InstrumentationManager.kt) to shutdown the instrumentation.
    fun shutdown() {
        instrumentationScope.cancel()
        isInstalled = false
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
        val wasInBackground = startedActivityCount == 0 && !configurationChangeInProgress

        if (wasInBackground) {
            instrumentationScope.launch { runCapture() }
        } else if (configurationChangeInProgress) {
            configurationChangeInProgress = false
        }
        startedActivityCount++
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
            instrumentationScope.launch { pauseCapture() }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
