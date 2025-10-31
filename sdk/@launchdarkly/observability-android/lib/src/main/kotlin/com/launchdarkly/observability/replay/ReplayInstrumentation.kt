package com.launchdarkly.observability.replay

import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability.replay"

// TODO: O11Y-625 - determine where these should be defined ultimately and tune accordingly.  Perhaps
// we don't need a batching exporter in this layer.  Perhaps this layer shouldn't be the one
// that decides the parameters of the batching exporter.  Perhaps the batching should be
// controlled by the instrumentation manager.
private const val BATCH_MAX_QUEUE_SIZE = 100
private const val BATCH_SCHEDULE_DELAY_MS = 1000L
private const val BATCH_EXPORTER_TIMEOUT_MS = 5000L
private const val BATCH_MAX_EXPORT_SIZE = 10

/**
 * Provides session replay instrumentation. Session replays that are sampled will appear on the LaunchDarkly dashboard.
 *
 * @param options Configuration options for replay behavior including privacy settings, capture interval, and backend URL
 *
 * @sample
 * ```kotlin
 *    val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
 *        .mobileKey("mobile-key-123abc")
 *        .plugins(
 *            Components.plugins().setPlugins(
 *                Collections.singletonList<Plugin>(
 *                    Observability(
 *                        this@BaseApplication,
 *                        Options(
 *                            resourceAttributes = Attributes.of(
 *                                AttributeKey.stringKey("serviceName"), "example-service"
 *                            ),
 *                            instrumentations = listOf(
 *                                ReplayInstrumentation(
 *                                    options = ReplayOptions(
 *                                        privacyProfile = PrivacyProfile.STRICT,
 *                                    )
 *                                )
 *                            )
 *                        )
 *                    )
 *                )
 *            )
 *        )
 *        .build();
 * ```
 *
 * @see ReplayOptions for configuration options
 * @see PrivacyProfile for privacy settings
 */
class ReplayInstrumentation(
    private val options: ReplayOptions = ReplayOptions(),
) : LDExtendedInstrumentation {

    private lateinit var _otelLogger: Logger
    private lateinit var _captureSource: CaptureSource
    
    private var _captureJob: Job? = null
    private var _isPaused: Boolean = false
    private val _captureMutex = Mutex()

    override val name: String = INSTRUMENTATION_SCOPE_NAME

    override fun install(ctx: InstallationContext) {
        _otelLogger = ctx.openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)
        _captureSource = CaptureSource(ctx.sessionManager, options.privacyProfile.asMatchersList())
        _captureSource.attachToApplication(ctx.application)

        // TODO: O11Y-621 - don't use global scope
        // TODO: O11Y-621 - shutdown procedure and cleanup of dispatched jobs
        GlobalScope.launch(Dispatchers.Default) {
            _captureSource.captureFlow.collect { capture ->
                _otelLogger.logRecordBuilder()
                    .setAttribute("event.domain", "media")
                    .setAttribute("image.width", capture.origWidth.toLong())
                    .setAttribute("image.height", capture.origHeight.toLong())
                    .setAttribute("image.data", capture.imageBase64)
                    .setAttribute("session.id", capture.session)
                    .setTimestamp(capture.timestamp, TimeUnit.MILLISECONDS)
                    .emit()
            }
        }
        
        // Start periodic capture automatically
        internalStartCapture()
    }

    // TODO: O11Y-622 - implement mechanism for customer code to invoke this method
    suspend fun runCapture() {
        _captureMutex.withLock {
            // If already running (not paused), do nothing
            if (!_isPaused) {
                return
            }
            
            // Clear paused flag and start/resume periodic capture
            _isPaused = false
            internalStartCapture()
        }
    }

    // TODO: O11Y-622 - implement mechanism for customer code to invoke this method
    suspend fun pauseCapture() {
        _captureMutex.withLock {
            // if already paused, do nothing
            if (_isPaused) {
                return
            }
            
            // pause the periodic capture by terminating the job
            _isPaused = true
            _captureJob?.cancel()
            _captureJob = null
        }
    }
    
    private fun internalStartCapture() {
        // TODO: O11Y-621 - don't use global scope
        _captureJob = GlobalScope.launch(Dispatchers.Default) {
            try {
                while (true) {
                    // Perform capture
                    _captureSource.captureNow()
                    delay(options.capturePeriodMillis)
                }
            } finally {
                // Job completed or was cancelled
            }
        }
    }

    override fun getLoggerScopeName(): String = INSTRUMENTATION_SCOPE_NAME

    override fun getLogRecordProcessor(credential: String): LogRecordProcessor {
        val exporter = RRwebGraphQLReplayLogExporter(
            organizationVerboseId = credential, // the SDK credential is used as the organization ID intentionally
            backendUrl = options.backendUrl,
            serviceName = options.serviceName,
            serviceVersion = options.serviceVersion,
        )

        return BatchLogRecordProcessor.builder(exporter)
            .setMaxQueueSize(BATCH_MAX_QUEUE_SIZE)
            .setScheduleDelay(BATCH_SCHEDULE_DELAY_MS, TimeUnit.MILLISECONDS)
            .setExporterTimeout(BATCH_EXPORTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(BATCH_MAX_EXPORT_SIZE)
            .build()
    }
}
