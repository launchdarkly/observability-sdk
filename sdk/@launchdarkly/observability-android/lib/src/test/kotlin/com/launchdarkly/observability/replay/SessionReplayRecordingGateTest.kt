package com.launchdarkly.observability.replay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionReplayRecordingGateTest {

    @Test
    fun `a launch with no cached failure records from the start`() {
        val gate = SessionReplayRecordingGate(hasCachedFailure = false)

        assertTrue(gate.isScreenCaptureAllowed)
        assertTrue(gate.canStartRecording)
        assertFalse(gate.isAwaitingVerdict)

        // The session was accepted, which is what the gate already assumed.
        assertEquals(
            SessionReplayRecordingGate.Outcome.None,
            gate.apply(SessionReplayInitializationVerdict.Allowed)
        )
        assertTrue(gate.isScreenCaptureAllowed)
    }

    @Test
    fun `a cached failure withholds screenshots until the backend accepts the session`() {
        val gate = SessionReplayRecordingGate(hasCachedFailure = true)

        assertFalse(gate.isScreenCaptureAllowed)
        assertTrue(gate.canStartRecording)
        assertTrue(gate.isAwaitingVerdict)

        assertEquals(
            SessionReplayRecordingGate.Outcome.ReleaseScreenCapture,
            gate.apply(SessionReplayInitializationVerdict.Allowed)
        )
        assertTrue(gate.isScreenCaptureAllowed)
        assertFalse(gate.isAwaitingVerdict)

        // Later sessions initialize too; the failure is already cleared, so there is nothing to do.
        assertEquals(
            SessionReplayRecordingGate.Outcome.None,
            gate.apply(SessionReplayInitializationVerdict.Allowed)
        )
    }

    @Test
    fun `a refusal ends recording for the launch`() {
        val gate = SessionReplayRecordingGate(hasCachedFailure = false)

        assertEquals(
            SessionReplayRecordingGate.Outcome.StopRecording("blocked"),
            gate.apply(SessionReplayInitializationVerdict.Unrecoverable("blocked"))
        )
        assertFalse(gate.isScreenCaptureAllowed)
        assertFalse(gate.canStartRecording)
        // Nothing is owed anymore: the exporter stops talking to the backend until the next launch.
        assertFalse(gate.isAwaitingVerdict)

        assertEquals(
            SessionReplayRecordingGate.Outcome.None,
            gate.apply(SessionReplayInitializationVerdict.Unrecoverable("blocked again"))
        )
    }

    @Test
    fun `a refusal after a recovery still ends recording`() {
        val gate = SessionReplayRecordingGate(hasCachedFailure = true)
        assertEquals(
            SessionReplayRecordingGate.Outcome.ReleaseScreenCapture,
            gate.apply(SessionReplayInitializationVerdict.Allowed)
        )

        assertEquals(
            SessionReplayRecordingGate.Outcome.StopRecording("blocked"),
            gate.apply(SessionReplayInitializationVerdict.Unrecoverable("blocked"))
        )
        assertFalse(gate.isScreenCaptureAllowed)
        assertFalse(gate.canStartRecording)
    }

    @Test
    fun `an acceptance after a refusal cannot resume recording`() {
        val gate = SessionReplayRecordingGate(hasCachedFailure = true)
        assertEquals(
            SessionReplayRecordingGate.Outcome.StopRecording("blocked"),
            gate.apply(SessionReplayInitializationVerdict.Unrecoverable("blocked"))
        )

        assertEquals(
            SessionReplayRecordingGate.Outcome.None,
            gate.apply(SessionReplayInitializationVerdict.Allowed)
        )
        assertFalse(gate.isScreenCaptureAllowed)
        assertFalse(gate.canStartRecording)
    }
}
