package com.launchdarkly.observability.replay

import android.view.MotionEvent
import com.launchdarkly.observability.client.TouchSample
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Converts raw [TouchSample]s from the shared `UserInteractionManager` into scaled, grouped
 * [InteractionEvent]s for Session Replay.
 *
 * Window interception is no longer performed here; the Observability plugin owns the single touch
 * hook and this class is a pure consumer. Scaling (to match the scaled screenshots) and move
 * grouping (to reduce bandwidth) remain Session Replay concerns and live here.
 *
 * [process] is expected to be called from a single thread (the collector coroutine).
 *
 * @param sessionManager used to tag emitted events with the current session id.
 * @param scale the replay scale factor, or `null` for no scaling.
 * @param density the display density used to derive the pixel scale factor.
 */
class InteractionSource(
    private val sessionManager: SessionManager,
    private val scale: Float?,
    private val density: Float,
) {
    private val _captureEventFlow = MutableSharedFlow<InteractionEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val captureFlow: SharedFlow<InteractionEvent> = _captureEventFlow.asSharedFlow()

    private val _moveGrouper: InteractionMoveGrouper = InteractionMoveGrouper(sessionManager, _captureEventFlow)

    private val scaleFactor: Float = when {
        scale == null -> 1f
        density > 0f -> scale / density
        else -> 1f
    }

    /**
     * Processes a single raw touch sample. Must be invoked from a single thread; there are no
     * multi-threading protections (the move grouper is stateful).
     */
    fun process(sample: TouchSample) {
        val x = scaleCoordinate(sample.x, scaleFactor)
        val y = scaleCoordinate(sample.y, scaleFactor)

        when (sample.action) {
            MotionEvent.ACTION_DOWN -> {
                _captureEventFlow.tryEmit(
                    InteractionEvent(
                        action = MotionEvent.ACTION_DOWN,
                        positions = listOf(Position(x, y, sample.timestamp)),
                        session = sessionManager.getSessionId(),
                    )
                )
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                _moveGrouper.completeWithLastPosition(x, y, sample.timestamp)
                _captureEventFlow.tryEmit(
                    InteractionEvent(
                        // For the purposes of replay, CANCEL is treated as UP.
                        action = MotionEvent.ACTION_UP,
                        positions = listOf(Position(x, y, sample.timestamp)),
                        session = sessionManager.getSessionId(),
                    )
                )
            }
            MotionEvent.ACTION_MOVE -> {
                // The move grouper provides rate limiting and grouping by time and distance to
                // reduce bandwidth; it is responsible for calling tryEmit.
                _moveGrouper.handleMove(x, y, sample.timestamp)
            }
        }
    }
}
