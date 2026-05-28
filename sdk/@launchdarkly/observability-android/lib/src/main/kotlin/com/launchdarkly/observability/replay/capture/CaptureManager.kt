package com.launchdarkly.observability.replay.capture

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.replay.ReplayOptions
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A source of [ExportFrame]s taken from the lowest visible window. Captures
 * are emitted on the [captureFlow] property of this class.
 *
 * @param sessionManager Used to get current session for tagging [ExportFrame] with session id
 */
class CaptureManager(
    private val sessionManager: SessionManager,
    private val options: ReplayOptions,
    private val logger: ObserveLogger,
    private val imageCaptureService: ImageCaptureServicing = ImageCaptureService(options, logger),
    // TODO: O11Y-628 - add captureQuality options
) {
    private val _captureEventFlow = MutableSharedFlow<ExportFrame>()
    val captureFlow: SharedFlow<ExportFrame> = _captureEventFlow.asSharedFlow()
    private val exportDiffManager = ExportDiffManager(
        compression = options.compression,
        scale = options.scale ?: 1f,
    )
    val captureDelayMillis: Long = if (options.frameRate > 0) {
        (1000.0 / options.frameRate).toLong().coerceAtLeast(1L)
    } else {
        Long.MAX_VALUE
    }

    /**
     * Requests a [ExportFrame] be taken now.
     */
    suspend fun captureNow() {
        val rawFrame = imageCaptureService.captureRawFrame() ?: return

        val session = sessionManager.getSessionId()
        val exportFrame = exportDiffManager.createCaptureEvent(rawFrame, session) ?: return
        _captureEventFlow.emit(exportFrame)
    }
}
