package com.launchdarkly.observability.client

import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionIdGenerator
import io.opentelemetry.android.session.SessionManager
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.sdk.common.Clock
import java.util.Collections.synchronizedList
import kotlin.time.Duration

/**
 * LaunchDarkly's own [SessionManager], used in place of OpenTelemetry Android's default so we can
 * **seed the initial session id** ([initialSessionId]). This lets the native observability instance
 * adopt a session id created elsewhere (e.g. by the JavaScript SDK on the same device), so that
 * spans, logs, metrics, and Session Replay all report the same `session.id`.
 *
 * Injected into [io.opentelemetry.android.OpenTelemetryRumBuilder] via
 * [io.opentelemetry.android.LDRumSessionManagerAccessor] so it backs OpenTelemetry Android's own
 * `session.id` span/log appenders as well — making it the single source of session identity.
 *
 * Rotation behavior mirrors OpenTelemetry Android's default `SessionManagerImpl` /
 * `SessionIdTimeoutHandler`:
 *  - a foreground session never times out;
 *  - a background gap of at least [backgroundInactivityTimeout] rotates the session on next use;
 *  - a session older than [maxLifetime] rotates regardless of foreground/background.
 *
 * Foreground/background transitions are fed in via [onApplicationForegrounded] /
 * [onApplicationBackgrounded] (wired from the service's app-lifecycle tracker).
 *
 * @param initialSessionId Optional session id to start with. When null or blank, an id is generated
 *   lazily on first use (matching the default manager).
 * @param backgroundInactivityTimeout Background inactivity after which the session rotates.
 * @param maxLifetime Absolute maximum session lifetime before rotation.
 * @param clock Time source; defaults to the OpenTelemetry default clock.
 * @param idGenerator Generator for new session ids; defaults to OpenTelemetry's.
 */
internal class LDSessionManager(
    initialSessionId: String? = null,
    private val backgroundInactivityTimeout: Duration,
    private val maxLifetime: Duration,
    private val clock: Clock = Clock.getDefault(),
    private val idGenerator: SessionIdGenerator = SessionIdGenerator.DEFAULT,
) : SessionManager {

    private val lock = Any()
    private val observers = synchronizedList(ArrayList<SessionObserver>())

    // Guarded by [lock].
    private var session: Session =
        if (!initialSessionId.isNullOrBlank()) {
            Session.DefaultSession(initialSessionId, clock.now())
        } else {
            // Empty id + (-1) timestamp forces generation on first getSessionId(), exactly like
            // the default manager's initial Session.NONE.
            Session.NONE
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
            if (sessionHasExpired() || hasTimedOut()) {
                candidate = Session.DefaultSession(idGenerator.generateSessionId(), clock.now())
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
        return newSession.getId()
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
        val elapsed = clock.now() - session.getStartTimestamp()
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
}
