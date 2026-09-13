package com.launchdarkly.observability.network

import com.launchdarkly.observability.BuildConfig
import java.util.Calendar

/**
 * Debug-only helper for exercising Session Replay's recoverable and unrecoverable error paths on a device
 * without a backend change. It fails `initializeSession` and `pushPayload` during even clock minutes and
 * lets them through during odd ones, so a single run alternates between the two states every minute.
 *
 * Nothing calls this: it is deliberately left unwired, and does nothing outside a debug build. To use it,
 * add the call at the top of [GraphQLClient.execute] and remove it again afterwards:
 *
 * ```kotlin
 * GraphQLFaultInjection.failIfNeeded(query)
 * ```
 */
internal object GraphQLFaultInjection {
    /** The failure to simulate. Change this to exercise a different branch of [ErrorRecoverability]. */
    enum class Mode {
        /**
         * A GraphQL error carrying `retryable: false`: unrecoverable, so Session Replay stops taking
         * screenshots immediately and the refusal is cached for the next launch.
         */
        NON_RETRYABLE_GRAPHQL_ERROR,

        /** `403`: unrecoverable by status code. */
        UNAUTHORIZED,

        /** `429`: recoverable, so events stay buffered and the call is retried. */
        TOO_MANY_REQUESTS,
    }

    val mode = Mode.NON_RETRYABLE_GRAPHQL_ERROR

    /** Fragments identifying the operations covered by the Session Replay error handling. */
    private val operationMarkers = listOf("mutation initializeSession", "mutation PushPayload")

    /**
     * Throws the simulated failure when [query] is one of the covered operations and this is a failing
     * minute.
     */
    fun failIfNeeded(query: String) {
        if (!BuildConfig.DEBUG) return
        if (operationMarkers.none { query.contains(it) }) return
        if (!isFailingMinute()) return

        throw when (mode) {
            Mode.NON_RETRYABLE_GRAPHQL_ERROR -> GraphQLClientException.GraphQLErrors(
                listOf(
                    GraphQLError(
                        message = "Session replay is not available in this region.",
                        extensions = GraphQLErrorExtensions(
                            code = "SESSION_REPLAY_BLOCKED_IN_REGION",
                            retryable = false,
                        ),
                    )
                )
            )

            Mode.UNAUTHORIZED -> GraphQLClientException.HttpStatus(403, "injected failure")
            Mode.TOO_MANY_REQUESTS -> GraphQLClientException.HttpStatus(429, "injected failure")
        }
    }

    private fun isFailingMinute(): Boolean = Calendar.getInstance().get(Calendar.MINUTE) % 2 == 0
}
