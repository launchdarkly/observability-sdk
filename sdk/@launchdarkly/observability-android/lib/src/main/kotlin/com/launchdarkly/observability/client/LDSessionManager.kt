package com.launchdarkly.observability.client

import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.android.session.SessionPublisher
import io.opentelemetry.sdk.common.Clock
import java.security.SecureRandom
import java.util.Collections.synchronizedList
import kotlin.time.Duration

/**
 * LaunchDarkly's own session source, used in place of OpenTelemetry Android's default so we can
 * **seed the initial session id** ([initialSessionId]). This lets the native observability instance
 * adopt a session id created elsewhere (e.g. by the JavaScript SDK on the same device), so that
 * spans, logs, metrics, and Session Replay all report the same `session.id`.
 *
 * Injected into [io.opentelemetry.android.OpenTelemetryRumBuilder] via `setSessionProvider` so it
 * backs OpenTelemetry Android's own `session.id` span/log appenders as well — making it the single
 * source of session identity.
 *
 * When [initialSessionId] is supplied the session is treated as *custom*: the caller owns the
 * session lifecycle, so automatic rotation is disabled and the seeded id is used for the lifetime
 * of this manager (mirrors iOS's `isCustomSession`).
 *
 * Otherwise rotation mirrors OpenTelemetry Android's default session manager /
 * `SessionIdTimeoutHandler`:
 *  - a foreground session never times out;
 *  - a background gap of at least [backgroundInactivityTimeout] rotates the session on next use;
 *  - a session older than [maxLifetime] rotates regardless of foreground/background.
 *
 * Foreground/background transitions are fed in via [onApplicationForegrounded] /
 * [onApplicationBackgrounded] (wired from the service's app-lifecycle tracker).
 *
 * @param initialSessionId Optional session id to start with. When non-blank the session is custom
 *   (never auto-rotated). When null or blank, an id is generated lazily on first use and rotated
 *   automatically (matching the default manager).
 * @param backgroundInactivityTimeout Background inactivity after which the session rotates.
 * @param maxLifetime Absolute maximum session lifetime before rotation.
 * @param clock Time source; defaults to the OpenTelemetry default clock.
 * @param idGenerator Generator for new session ids; defaults to a random 128-bit hex id.
 */
internal class LDSessionManager(
    initialSessionId: String? = null,
    private val backgroundInactivityTimeout: Duration,
    private val maxLifetime: Duration,
    private val clock: Clock = Clock.getDefault(),
    private val idGenerator: () -> String = ::randomSessionId,
) : SessionProvider, SessionPublisher {

    private val lock = Any()
    private val observers = synchronizedList(ArrayList<SessionObserver>())

    /**
     * Whether a session id was supplied externally. When true, the caller owns the session
     * lifecycle, so automatic rotation (background-inactivity timeout and max-lifetime) is disabled
     * and the seeded id is used for the lifetime of this manager. Mirrors iOS's `isCustomSession`.
     */
    private val isCustomSession: Boolean = !initialSessionId.isNullOrBlank()

    // Guarded by [lock].
    private var session: Session =
        if (isCustomSession) {
            LDSession(initialSessionId!!, clock.now())
        } else {
            // Empty id + zero timestamp forces generation on first read, exactly like the default
            // manager's initial "none" session.
            NONE_SESSION
        }

    // Timeout bookkeeping, mirroring SessionIdTimeoutHandler.
    @Volatile
    private var timeoutStartNanos: Long = clock.nanoTime()

    @Volatile
    private var state: State = State.FOREGROUND

    override fun addObserver(observer: SessionObserver) {
        observers.add(observer)
    }

    override fun getSessionId(): String {
        val newSession: Session
        val previousSession: Session
        val rotated: Boolean

        synchronized(lock) {
            var candidate = session
            // An externally supplied session id is never rotated; the caller owns its lifecycle.
            if (!isCustomSession && (sessionHasExpired() || hasTimedOut())) {
                candidate = LDSession(idGenerator(), clock.now())
            }
            // Bump the inactivity timer after deciding, before notifying (a new span may be created).
            bump()
            rotated = candidate !== session
            previousSession = session
            if (rotated) {
                session = candidate
            }
            newSession = session
        }

        if (rotated) {
            // Notify outside the lock; observers may create spans which call back into getSessionId().
            observers.forEach { observer ->
                observer.onSessionEnded(previousSession)
                observer.onSessionStarted(newSession, previousSession)
            }
        }
        return newSession.id
    }

    /** Marks the app as transitioning to the foreground; the next event settles it to foreground. */
    fun onApplicationForegrounded() {
        state = State.TRANSITIONING_TO_FOREGROUND
    }

    /** Marks the app as backgrounded, after which the inactivity timeout can rotate the session. */
    fun onApplicationBackgrounded() {
        state = State.BACKGROUND
    }

    private fun sessionHasExpired(): Boolean {
        val elapsed = clock.now() - session.startTimestamp
        return elapsed >= maxLifetime.inWholeNanoseconds
    }

    private fun hasTimedOut(): Boolean {
        // The session never times out while the app is in the foreground.
        if (state == State.FOREGROUND) return false
        val elapsed = clock.nanoTime() - timeoutStartNanos
        return elapsed >= backgroundInactivityTimeout.inWholeNanoseconds
    }

    private fun bump() {
        timeoutStartNanos = clock.nanoTime()
        // The first event after returning to the foreground settles the transitional state.
        if (state == State.TRANSITIONING_TO_FOREGROUND) {
            state = State.FOREGROUND
        }
    }

    private enum class State {
        FOREGROUND,
        BACKGROUND,

        /** Temporary state for the first event after the app is brought back to the foreground. */
        TRANSITIONING_TO_FOREGROUND,
    }

    private class LDSession(
        override val id: String,
        override val startTimestamp: Long,
    ) : Session

    private companion object {
        /**
         * Stands in for the default manager's "no session yet" value. The zero start timestamp
         * makes the first expiry check rotate immediately, generating the real id lazily.
         */
        private val NONE_SESSION: Session = LDSession("", 0L)
    }
}

private val sessionIdRandom = SecureRandom()

/** Generates a random 128-bit session id, rendered as 32 lowercase hex characters. */
private fun randomSessionId(): String {
    val bytes = ByteArray(16)
    sessionIdRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
