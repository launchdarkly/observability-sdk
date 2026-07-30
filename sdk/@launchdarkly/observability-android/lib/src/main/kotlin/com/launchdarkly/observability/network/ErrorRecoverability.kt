package com.launchdarkly.observability.network

import java.net.HttpURLConnection

/**
 * Implemented by exceptions that carry the recoverability verdict of the request that failed, so the
 * classification is made once — where the status code and the GraphQL `extensions` are still
 * available — rather than re-derived from an error message.
 */
interface RecoverableFailure {
    val isRecoverable: Boolean
}

/**
 * Classifies request failures as recoverable (retrying may succeed) or unrecoverable (permanent for
 * this launch). Lives outside Session Replay so every caller of the public graph can share one verdict
 * instead of re-deriving it from status codes.
 */
object ErrorRecoverability {
    private const val HTTP_TOO_MANY_REQUESTS = 429

    /**
     * Tests whether an HTTP error status represents a condition that might resolve on its own if we
     * retry.
     *
     * @param statusCode the HTTP status
     * @return `true` if retrying makes sense; `false` if it should be considered a permanent failure
     */
    fun isHttpErrorRecoverable(statusCode: Int): Boolean {
        if (statusCode !in 400..499) return true

        return when (statusCode) {
            HttpURLConnection.HTTP_BAD_REQUEST, // bad request
            HttpURLConnection.HTTP_CLIENT_TIMEOUT, // request timeout
            HTTP_TOO_MANY_REQUESTS -> true // too many requests
            else -> false // all other 4xx errors are unrecoverable
        }
    }

    /**
     * Classifies a [GraphQLResponse] that carries errors.
     */
    fun isErrorRecoverable(response: GraphQLResponse<*>): Boolean =
        isErrorRecoverable(response.httpStatusCode, response.errors)

    /**
     * Classifies a failed request from the status it returned and the GraphQL errors it carried.
     *
     * @param statusCode the HTTP status, or `null` when the request never reached one
     * @param errors the GraphQL errors the response carried, if any
     */
    fun isErrorRecoverable(statusCode: Int?, errors: List<GraphQLError>?): Boolean {
        // An explicit server verdict is more specific than the status code, so it wins.
        retryableFlag(errors)?.let { return it }
        // Without a status the request timed out or lost connectivity, neither of which is permanent.
        val status = statusCode ?: return true
        // The public graph reports rejections as `200` + `errors`, so those are classified like a
        // generic 4xx: unrecoverable unless the server marked an error retryable.
        if (status == HttpURLConnection.HTTP_OK) return errors.isNullOrEmpty()

        return isHttpErrorRecoverable(status)
    }

    /**
     * Classifies a thrown failure. Errors of unknown origin are treated as recoverable: retrying costs
     * a backed-off request, while a wrong permanent verdict silently disables a feature for the rest of
     * the launch.
     */
    fun isErrorRecoverable(error: Throwable): Boolean = (error as? RecoverableFailure)?.isRecoverable ?: true

    /**
     * The server's retry verdict for a set of GraphQL errors, or `null` when none of them states one.
     */
    private fun retryableFlag(errors: List<GraphQLError>?): Boolean? {
        if (errors.isNullOrEmpty()) return null
        if (errors.any { it.extensions?.retryable == false }) return false
        if (errors.any { it.extensions?.retryable == true }) return true

        return null
    }
}
