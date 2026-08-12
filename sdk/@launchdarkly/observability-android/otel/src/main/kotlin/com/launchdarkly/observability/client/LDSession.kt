package com.launchdarkly.observability.client

import io.opentelemetry.api.trace.TraceId
import java.util.Random

/**
 * A single observability session: the unit every signal is stamped with via `session.id`.
 *
 * These types deliberately mirror OpenTelemetry Android's session API rather than reusing it, so
 * the OTel-only product can drop the `io.opentelemetry.android:*` artifacts altogether. The full
 * product adapts [LDSessionManaging] back onto OpenTelemetry Android's `SessionManager` so its RUM
 * pipeline still reports the same session id.
 */
interface LDSession {
    fun getId(): String

    fun getStartTimestamp(): Long

    companion object {
        /** The absent session. An empty id forces generation on first use. */
        val NONE = DefaultSession("", -1)
    }

    /** Two sessions are the same session when they carry the same id, regardless of start time. */
    data class DefaultSession(
        private val id: String,
        private val timestamp: Long,
    ) : LDSession {
        override fun getId(): String = id

        override fun getStartTimestamp(): Long = timestamp

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DefaultSession

            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }
}

/** Notified when a session rotates, so consumers can reset per-session state. */
interface LDSessionObserver {
    fun onSessionStarted(
        newSession: LDSession,
        previousSession: LDSession,
    )

    fun onSessionEnded(session: LDSession)
}

/**
 * Supplies the current session id and publishes rotations. Implemented by [LDSessionManager] and
 * consumed anywhere a signal needs stamping.
 */
interface LDSessionManaging {
    fun getSessionId(): String

    fun addObserver(observer: LDSessionObserver)
}

/** Generates session ids. Separated from [LDSessionManager] so tests can supply a fixed sequence. */
interface LDSessionIdGenerator {
    fun generateSessionId(): String

    /** Produces a 128-bit id in the same hex form as a trace id, matching other LaunchDarkly SDKs. */
    object DEFAULT : LDSessionIdGenerator {
        override fun generateSessionId(): String {
            val random = Random()
            return TraceId.fromLongs(random.nextLong(), random.nextLong())
        }
    }
}
