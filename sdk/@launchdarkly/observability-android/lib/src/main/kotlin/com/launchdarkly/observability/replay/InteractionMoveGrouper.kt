package com.launchdarkly.observability.replay

import android.view.MotionEvent
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.flow.MutableSharedFlow

private const val FILTER_THRESHOLD_DISTANCE_SQUARED_PIXELS = 144 // 12 X 12 pixels, matches iOS tapMaxDistanceSquared
private const val FILTER_THRESHOLD_TIME_MILLIS = 40 // matches iOS touchMoveThrottle (0.04s)
private const val EMIT_PERIOD_MILLIS = 240 // time to gather 4 positions into group on average (0.24s)

/**
 * Class for filtering and grouping emissions of movement interactions to reduce data rates.
 *
 * This class can only be used from a single thread, usually the main thread.
 *
 * @param _sessionManager used for tagging events with session id
 * @param _bufferedFlow a buffered flow that emitted events will be given to, this is buffered to
 * avoid delays handing off data to collector of flow
 */
class InteractionMoveGrouper(
    private val _sessionManager: SessionManager,
    private val _bufferedFlow: MutableSharedFlow<InteractionEvent>,
) {
    private val acceptedPositions = mutableListOf<Position>()
    private var lastAccepted : Position? = null
    private var lastEmitTime = 0L

    // Handles another move applying filtering and possibly invoking tryEmit on the buffered flow when necessary.
    fun handleMove(x: Int, y: Int, timestamp: Long) {
        val current = Position(x, y, timestamp)

        val last = lastAccepted
        val passedFilter: Boolean
        if (last != null) {
            // if we have a last position, only use this new position if it passes filter (both thresholds have been exceeded)
            val distThresholdExceeded = distanceSq(
                current,
                last
            ) > FILTER_THRESHOLD_DISTANCE_SQUARED_PIXELS // note this is square of distance in pixels
            val timeThresholdExceeded = (timestamp - last.timestamp) > FILTER_THRESHOLD_TIME_MILLIS
            passedFilter = distThresholdExceeded && timeThresholdExceeded
        } else {
            // if we don't have a last position that got through filtering, this one is the first and should be used
            passedFilter = true
        }

        if (passedFilter) {
            // position has passed filtering, add it to list
            acceptedPositions.add(current)
            lastAccepted = current
        }

        // if enough time has passed since last emission, tryEmit the group
        if (acceptedPositions.isNotEmpty() && (timestamp - lastEmitTime > EMIT_PERIOD_MILLIS)) {
            val interaction = InteractionEvent(
                action = MotionEvent.ACTION_MOVE,
                positions = acceptedPositions.toList(), // toList() makes a copy, which is required
                session = _sessionManager.getSessionId(),
            )
            _bufferedFlow.tryEmit(interaction)

            lastEmitTime = timestamp
            acceptedPositions.clear()
        }
    }

    // Call this when the last position of a move is known, this will trigger tryEmit on the buffered flow so no
    // positions are left behind in the buffer.
    fun completeWithLastPosition(x: Int, y: Int, timestamp: Long) {
        val current = Position(x, y, timestamp)

        // last position that got through filtering
        val last = lastAccepted
        if (last == null || last != current) {
            acceptedPositions.add(current)
            lastAccepted = current
        }

        // since we have distance and time filtering, it is possible that there are no positions that
        // make it through the filtering, need to protect against this case
        if (acceptedPositions.isNotEmpty()) {
            val interaction = InteractionEvent(
                action = MotionEvent.ACTION_MOVE,
                positions = acceptedPositions.toList(), // toList() makes a copy, which is required
                session = _sessionManager.getSessionId(),
            )
            _bufferedFlow.tryEmit(interaction)
        }

        // reset state so next move sequence is treated as an independent sequence
        lastAccepted = null
        lastEmitTime = 0L
        acceptedPositions.clear()
    }

    // Calculates squared distance between positions
    private fun distanceSq(a: Position, b: Position): Int {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }
}
