/**
 * Originally from https://github.com/open-telemetry/opentelemetry-android/blob/main/android-agent/src/main/kotlin/io/opentelemetry/android/agent/session/SessionManager.kt
 *
 * Was publicly available before 0.14.0-alpha and this implementation meets our needs. There are a couple thread safety concerns in this code,
 * but we expect those to be addressed by the otel-android maintainers. Rather than fix them ourselves and deviate/fork, we will come back
 * to check for any updates before this is released in a 1.X version of our plugin.  O11Y-443 tracks this task.
 *
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.launchdarkly.observability.vendored.otel

import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.android.agent.session.SessionIdGenerator
import io.opentelemetry.android.agent.session.SessionStorage
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.android.session.SessionPublisher
import io.opentelemetry.sdk.common.Clock
import java.util.Collections.synchronizedList
import kotlin.time.Duration

internal class SessionManager(
    private val clock: Clock = Clock.getDefault(),
    private val sessionStorage: SessionStorage = SessionStorage.InMemory(),
    private val timeoutHandler: SessionIdTimeoutHandler,
    private val idGenerator: SessionIdGenerator = SessionIdGenerator.DEFAULT,
    private val maxSessionLifetime: Duration,
) : SessionProvider,
    SessionPublisher {
    // TODO: Make thread safe / wrap with AtomicReference?
    private var session: Session = Session.NONE
    private val observers = synchronizedList(ArrayList<SessionObserver>())

    init {
        sessionStorage.save(session)
    }

    override fun addObserver(observer: SessionObserver) {
        observers.add(observer)
    }

    override fun getSessionId(): String {
        // value will never be null
        var newSession = session

        if (sessionHasExpired() || timeoutHandler.hasTimedOut()) {
            val newId = idGenerator.generateSessionId()

            // TODO FIXME: This is not threadsafe -- if two threads call getSessionId()
            // at the same time while timed out, two new sessions are created
            // Could require SessionStorage impls to be atomic/threadsafe or
            // do the locking in this class?

            newSession = Session.DefaultSession(newId, clock.now())
            sessionStorage.save(newSession)
        }

        timeoutHandler.bump()

        // observers need to be called after bumping the timer because it may
        // create a new span
        if (newSession != session) {
            val previousSession = session
            session = newSession
            observers.forEach {
                it.onSessionEnded(previousSession)
                it.onSessionStarted(session, previousSession)
            }
        }
        return session.getId()
    }

    private fun sessionHasExpired(): Boolean {
        val elapsedTime = clock.now() - session.getStartTimestamp()
        return elapsedTime >= maxSessionLifetime.inWholeNanoseconds
    }

    companion object {
        @JvmStatic
        fun create(
            timeoutHandler: SessionIdTimeoutHandler,
            sessionConfig: SessionConfig,
        ): SessionManager =
            SessionManager(
                timeoutHandler = timeoutHandler,
                maxSessionLifetime = sessionConfig.maxLifetime,
            )
    }
}
