package com.launchdarkly.observability.client

import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionManager
import io.opentelemetry.android.session.SessionObserver

/**
 * Presents LaunchDarkly's [LDSessionManaging] as an OpenTelemetry Android [SessionManager].
 *
 * Session identity is owned by the core, which knows nothing about OpenTelemetry Android. The full
 * product still builds its pipeline with OpenTelemetry Android RUM, whose `session.id` appenders
 * read from a [SessionManager], so this adapter points them at the same source. Without it the RUM
 * pipeline would mint its own session ids and signals would disagree with Session Replay.
 */
internal class RumSessionManagerAdapter(
    private val delegate: LDSessionManaging,
) : SessionManager {

    override fun getSessionId(): String = delegate.getSessionId()

    override fun addObserver(observer: SessionObserver) {
        delegate.addObserver(object : LDSessionObserver {
            override fun onSessionStarted(newSession: LDSession, previousSession: LDSession) {
                observer.onSessionStarted(newSession.toOtelSession(), previousSession.toOtelSession())
            }

            override fun onSessionEnded(session: LDSession) {
                observer.onSessionEnded(session.toOtelSession())
            }
        })
    }
}

private fun LDSession.toOtelSession(): Session =
    Session.DefaultSession(getId(), getStartTimestamp())
