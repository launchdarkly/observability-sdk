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
 * GraphQLFaultInjection.responseIfNeeded<T>(query)?.let { return@withContext it }
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
     * The simulated failure for [query], or `null` when the call should be left alone.
     */
    fun <T> responseIfNeeded(query: String): GraphQLResponse<T>? {
        if (!BuildConfig.DEBUG) return null
        if (operationMarkers.none { query.contains(it) }) return null
        if (!isFailingMinute()) return null

        return when (mode) {
            Mode.NON_RETRYABLE_GRAPHQL_ERROR -> GraphQLResponse(
                data = null,
                errors = listOf(
                    GraphQLError(
                        message = "Session replay is not available in this region.",
                        extensions = GraphQLErrorExtensions(
                            code = "SESSION_REPLAY_BLOCKED_IN_REGION",
                            retryable = false,
                        ),
                    )
                ),
                httpStatusCode = 200,
            )

            Mode.UNAUTHORIZED -> httpFailure(403)
            Mode.TOO_MANY_REQUESTS -> httpFailure(429)
        }
    }

    private fun <T> httpFailure(statusCode: Int): GraphQLResponse<T> = GraphQLResponse(
        data = null,
        errors = listOf(GraphQLError(message = "HTTP Error $statusCode: injected failure")),
        httpStatusCode = statusCode,
    )

    private fun isFailingMinute(): Boolean = Calendar.getInstance().get(Calendar.MINUTE) % 2 == 0
}
