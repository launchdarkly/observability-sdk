package com.launchdarkly.observability.replay

import com.launchdarkly.observability.interfaces.InstrumentationLoggerProvider
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

// TODO: determine where these should be defined ultimately and tune accordingly.  Perhaps
// we don't need a batching exporter in this layer.  Perhaps this layer shouldn't be the one
// that decides the parameters of the batching exporter.  Perhaps the batching should be
// controlled by the instrumentation manager.  Perhaps the world is flat.
private const val BATCH_MAX_QUEUE_SIZE = 100
private const val BATCH_SCHEDULE_DELAY_MS = 1000L
private const val BATCH_EXPORTER_TIMEOUT_MS = 5000L
private const val BATCH_MAX_EXPORT_SIZE = 10
private const val METRICS_EXPORT_INTERVAL_SECONDS = 10L
private const val FLUSH_TIMEOUT_SECONDS = 5L

// TODO: shutdown procedure and cleanup of dispatched jobs

class ReplayInstrumentation(
    val options: ReplayOptions = ReplayOptions(),
) : AndroidInstrumentation, InstrumentationLoggerProvider {

    private lateinit var _logger: Logger
    private lateinit var _captureSource: CaptureSource
    
    // State management for periodic capture
    private var _captureJob: Job? = null
    private val _isPaused = AtomicBoolean(false)
    private val _captureMutex = Mutex()

    override val name: String = INSTRUMENTATION_SCOPE_NAME

    override fun install(ctx: InstallationContext) {
        _logger = ctx.openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)

        _captureSource = CaptureSource(ctx.sessionManager, options.privacyProfile)
        _captureSource.attachApplication(ctx.application)

        // TODO: don't use global scope
        GlobalScope.launch(Dispatchers.Default) {
            var firstScreenshotSent = false;
            _captureSource.captureFlow.collect { capture ->
                _logger.logRecordBuilder()
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
        startPeriodicCapture()
    }

    suspend fun runCapture() {
        _captureMutex.withLock {
            // If already running (not paused), do nothing
            if (!_isPaused.get()) {
                return
            }
            
            // Clear paused flag and start/resume periodic capture
            _isPaused.set(false)
            startPeriodicCapture()
        }
    }

    suspend fun pauseCapture() {
        _captureMutex.withLock {
            // If already paused, do nothing
            if (_isPaused.get()) {
                return
            }
            
            // Pause the periodic capture by terminating the job
            _isPaused.set(true)
            _captureJob?.cancel()
            _captureJob = null
        }
    }
    
    private fun startPeriodicCapture() {
        // Start new periodic capture job
        // TODO: don't use global scope
        _captureJob = GlobalScope.launch(Dispatchers.Default) {
            try {
                while (true) {
                    // Perform capture
                    _captureSource.captureNow()
                    
                    // Wait for the specified interval
                    delay(options.captureIntervalMillis)
                }
            } finally {
                // Job completed or was cancelled
            }
        }
    }

    override fun getLoggerScopeName(): String = INSTRUMENTATION_SCOPE_NAME

    override fun getLogRecordProcessor(): LogRecordProcessor {
        val exporter = RRwebGraphQLReplayLogExporter()
        return BatchLogRecordProcessor.builder(exporter)
            .setMaxQueueSize(BATCH_MAX_QUEUE_SIZE)
            .setScheduleDelay(BATCH_SCHEDULE_DELAY_MS, TimeUnit.MILLISECONDS)
            .setExporterTimeout(BATCH_EXPORTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(BATCH_MAX_EXPORT_SIZE)
            .build()
    }
}