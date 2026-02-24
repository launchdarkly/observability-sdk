package com.launchdarkly.observability.replay

import android.view.MotionEvent
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InteractionMoveGrouperTest {

    private lateinit var mockSessionManager: SessionManager
    private lateinit var bufferedFlow: MutableSharedFlow<InteractionEvent>
    private lateinit var grouper: InteractionMoveGrouper

    @BeforeEach
    fun setUp() {
        mockSessionManager = mockk<SessionManager>(relaxed = true)
        every { mockSessionManager.getSessionId() } returns "test-session-id"
        bufferedFlow = MutableSharedFlow(replay = 64, extraBufferCapacity = 64)
        grouper = InteractionMoveGrouper(mockSessionManager, bufferedFlow)
    }

    @Test
    fun `first position is always accepted when no previous position exists`() = runTest {
        // Act
        grouper.handleMove(x = 100, y = 200, timestamp = 1000L)
        grouper.completeWithLastPosition(x = 100, y = 200, timestamp = 1000L)

        // Assert - collect emitted events
        val events = bufferedFlow.take(1).toList()
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(MotionEvent.ACTION_MOVE, event.action)
        assertEquals("test-session-id", event.session)
        assertEquals(1, event.positions.size)
        assertEquals(Position(100, 200, 1000L), event.positions[0])
    }

    @Test
    fun `first position is added to acceptedPositions`() = runTest {
        // Act
        grouper.handleMove(x = 50, y = 75, timestamp = 2000L)
        grouper.completeWithLastPosition(x = 50, y = 75, timestamp = 2000L)

        // Assert
        val events = bufferedFlow.take(1).toList()
        assertEquals(1, events.size)
        val event = events[0]
        assertTrue(event.positions.isNotEmpty())
        assertEquals(Position(50, 75, 2000L), event.positions.first())
    }

    @Test
    fun `first position does not trigger emission immediately`() = runTest {
        // Act - add first position
        grouper.handleMove(x = 100, y = 200, timestamp = 1000L)

        // Assert - verify position was stored but not emitted by completing and checking
        // If it was emitted immediately, completeWithLastPosition would emit an empty or different list
        grouper.completeWithLastPosition(x = 100, y = 200, timestamp = 1000L)
        val events = bufferedFlow.take(1).toList()
        assertEquals(1, events.size)
        val event = events[0]
        // Verify the position from handleMove is in the emitted event
        assertEquals(Position(100, 200, 1000L), event.positions[0])
    }

    @Test
    fun `first position with zero coordinates is accepted`() = runTest {
        // Act
        grouper.handleMove(x = 0, y = 0, timestamp = 1000L)
        grouper.completeWithLastPosition(x = 0, y = 0, timestamp = 1000L)

        // Assert
        val events = bufferedFlow.take(1).toList()
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(1, event.positions.size)
        assertEquals(Position(0, 0, 1000L), event.positions[0])
    }

    @Test
    fun `first position with negative coordinates is accepted`() = runTest {
        // Act
        grouper.handleMove(x = -10, y = -20, timestamp = 1000L)
        grouper.completeWithLastPosition(x = -10, y = -20, timestamp = 1000L)

        // Assert
        val events = bufferedFlow.take(1).toList()
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(1, event.positions.size)
        assertEquals(Position(-10, -20, 1000L), event.positions[0])
    }

    @Test
    fun `first position with large coordinates is accepted`() = runTest {
        // Act
        grouper.handleMove(x = 10000, y = 20000, timestamp = 1000L)
        grouper.completeWithLastPosition(x = 10000, y = 20000, timestamp = 1000L)

        // Assert
        val events = bufferedFlow.take(1).toList()
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(1, event.positions.size)
        assertEquals(Position(10000, 20000, 1000L), event.positions[0])
    }

    @Test
    fun `first position with zero timestamp is accepted`() = runTest {
        // Act
        grouper.handleMove(x = 100, y = 200, timestamp = 0L)
        grouper.completeWithLastPosition(x = 100, y = 200, timestamp = 0L)

        // Assert
        val events = bufferedFlow.take(1).toList()
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(1, event.positions.size)
        assertEquals(Position(100, 200, 0L), event.positions[0])
    }

    @Test
    fun `first position with large timestamp is accepted`() = runTest {
        // Act
        grouper.handleMove(x = 100, y = 200, timestamp = Long.MAX_VALUE)
        grouper.completeWithLastPosition(x = 100, y = 200, timestamp = Long.MAX_VALUE)

        // Assert
        val events = bufferedFlow.take(1).toList()
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(1, event.positions.size)
        assertEquals(Position(100, 200, Long.MAX_VALUE), event.positions[0])
    }

    @Test
    fun `position is rejected when distance threshold not exceeded`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 111, y = 100, timestamp = 1041L) // will be ignored since distance is too short (distance²=121 < 144) even though time is enough (41ms > 40ms)
        grouper.completeWithLastPosition(x = 113, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(1, events[1].positions.size)
        assertEquals(Position(113, 100, 1082L), events[1].positions[0])
    }

    @Test
    fun `position is accepted when distance threshold exceeded`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 113, y = 100, timestamp = 1041L) // will be accepted since distance is far enough (distance²=169 > 144) and time is enough (41ms > 40ms)
        grouper.completeWithLastPosition(x = 126, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(2, events[1].positions.size)
        assertEquals(Position(113, 100, 1041L), events[1].positions[0])
        assertEquals(Position(126, 100, 1082L), events[1].positions[1])
    }

    @Test
    fun `position at exact distance threshold is rejected`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 112, y = 100, timestamp = 1041L) // will be ignored since distance is exactly at threshold (distance²=144, not > 144)
        grouper.completeWithLastPosition(x = 113, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(1, events[1].positions.size)
        assertEquals(Position(113, 100, 1082L), events[1].positions[0])
    }

    @Test
    fun `diagonal distance is calculated correctly`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 109, y = 109, timestamp = 1041L) // will be accepted since diagonal distance is far enough (distance²=162 > 144)
        grouper.completeWithLastPosition(x = 120, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(2, events[1].positions.size)
        assertEquals(Position(109, 109, 1041L), events[1].positions[0])
        assertEquals(Position(120, 100, 1082L), events[1].positions[1])
    }

    // Time filtering tests
    @Test
    fun `position is rejected when time threshold not exceeded`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 113, y = 100, timestamp = 1039L) // will be ignored since time is too short (39ms <= 40ms) even though distance is enough (distance²=169 > 144)
        grouper.completeWithLastPosition(x = 126, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(1, events[1].positions.size)
        assertEquals(Position(126, 100, 1082L), events[1].positions[0])
    }

    @Test
    fun `position is accepted when time threshold exceeded`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 113, y = 100, timestamp = 1041L) // will be accepted since time is long enough (41ms > 40ms) and distance is enough (distance²=169 > 144)
        grouper.completeWithLastPosition(x = 126, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(2, events[1].positions.size)
        assertEquals(Position(113, 100, 1041L), events[1].positions[0])
        assertEquals(Position(126, 100, 1082L), events[1].positions[1])
    }

    @Test
    fun `position at exact time threshold is rejected`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 113, y = 100, timestamp = 1040L) // will be ignored since time is exactly at threshold (40ms, not > 40ms)
        grouper.completeWithLastPosition(x = 126, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(1, events[1].positions.size)
        assertEquals(Position(126, 100, 1082L), events[1].positions[0])
    }

    // Combined filtering tests
    @Test
    fun `position is rejected when only distance threshold exceeded`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 113, y = 100, timestamp = 1039L) // will be ignored since time is too short (39ms <= 40ms) even though distance is enough (distance²=169 > 144)
        grouper.completeWithLastPosition(x = 126, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(1, events[1].positions.size)
        assertEquals(Position(126, 100, 1082L), events[1].positions[0])
    }

    @Test
    fun `position is rejected when only time threshold exceeded`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 111, y = 100, timestamp = 1041L) // will be ignored since distance is too short (distance²=121 < 144) even though time is enough (41ms > 40ms)
        grouper.completeWithLastPosition(x = 126, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(1, events[1].positions.size)
        assertEquals(Position(126, 100, 1082L), events[1].positions[0])
    }

    @Test
    fun `position is accepted when both thresholds exceeded`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 113, y = 100, timestamp = 1041L) // will be accepted since both distance (distance²=169 > 144) and time (41ms > 40ms) are enough
        grouper.completeWithLastPosition(x = 126, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(2, events[1].positions.size)
        assertEquals(Position(113, 100, 1041L), events[1].positions[0])
        assertEquals(Position(126, 100, 1082L), events[1].positions[1])
    }

    @Test
    fun `position is rejected when neither threshold exceeded`() = runTest {
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L) // leads to emission immediately since last emit time is 0
        grouper.handleMove(x = 111, y = 100, timestamp = 1039L) // will be ignored since both distance (distance²=121 < 144) and time (39ms <= 40ms) are too short
        grouper.completeWithLastPosition(x = 126, y = 100, timestamp = 1082L) // leads to emission with this position
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])

        assertEquals(1, events[1].positions.size)
        assertEquals(Position(126, 100, 1082L), events[1].positions[0])
    }

    @Test
    fun `multiple positions filtered correctly in sequence`() = runTest {
        // First position triggers immediate emission since lastEmitTime is 0
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L)
        
        // Position 2: rejected (distance²=121 < 144, time=30ms < 40ms from position 1)
        grouper.handleMove(x = 111, y = 100, timestamp = 1030L)
        // Position 3: accepted (distance²=169 > 144, time=41ms > 40ms from position 1)
        grouper.handleMove(x = 113, y = 100, timestamp = 1041L)
        // Position 4: rejected (distance²=4 < 144, time=5ms < 40ms from position 3)
        grouper.handleMove(x = 115, y = 100, timestamp = 1046L)
        // Position 5: accepted (distance²=169 > 144, time=41ms > 40ms from position 3)
        grouper.handleMove(x = 126, y = 100, timestamp = 1082L)
        
        // completeWithLastPosition emits positions 3, 5 from above and this position
        grouper.completeWithLastPosition(x = 139, y = 100, timestamp = 1123L)
        
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        
        // First event: position 1 (emitted immediately by first handleMove)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])
        
        // Second event: positions 3, 5, and complete position (emitted by completeWithLastPosition)
        assertEquals(3, events[1].positions.size)
        assertEquals(Position(113, 100, 1041L), events[1].positions[0])
        assertEquals(Position(126, 100, 1082L), events[1].positions[1])
        assertEquals(Position(139, 100, 1123L), events[1].positions[2])
    }

    @Test
    fun `same position with different timestamp is filtered correctly`() = runTest {
        // First position triggers immediate emission since lastEmitTime is 0
        grouper.handleMove(x = 100, y = 100, timestamp = 1000L)
        
        // Same position (distance²=0 < 144) so it is filtered out by handleMove
        grouper.handleMove(x = 100, y = 100, timestamp = 1041L)
        
        // completeWithLastPosition adds the position (since last != current due to different timestamp) and emits
        grouper.completeWithLastPosition(x = 100, y = 100, timestamp = 1082L)
        
        val events = bufferedFlow.take(2).toList()
        assertEquals(2, events.size)
        
        // First event: position 1 (emitted immediately by first handleMove)
        assertEquals(1, events[0].positions.size)
        assertEquals(Position(100, 100, 1000L), events[0].positions[0])
        
        // Second event: position with different timestamp (emitted by completeWithLastPosition)
        assertEquals(1, events[1].positions.size)
        assertEquals(Position(100, 100, 1082L), events[1].positions[0])
    }
}

