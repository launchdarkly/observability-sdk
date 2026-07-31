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
     * Classifies any failure thrown by [GraphQLClient]. Errors of unknown origin are treated as
     * recoverable: retrying costs a backed-off request, while a wrong permanent verdict silently disables
     * a feature for the rest of the launch.
     */
    fun isErrorRecoverable(error: Throwable): Boolean = when (error) {
        is GraphQLClientException -> isRecoverable(error)
        is RecoverableFailure -> error.isRecoverable
        else -> true
    }

    private fun isRecoverable(error: GraphQLClientException): Boolean = when (error) {
        // A rejected request can still carry a GraphQL envelope, and an explicit `retryable` there is more
        // specific than the status code, so it wins.
        is GraphQLClientException.HttpStatus ->
            retryableFlag(error.errors) ?: isHttpErrorRecoverable(error.statusCode)

        // The public graph reports rejections as `200` + `errors`, so these are classified like a generic
        // 4xx: unrecoverable unless the server marks an error retryable.
        is GraphQLClientException.GraphQLErrors -> retryableFlag(error.errors) ?: false

        // Timeouts, connectivity loss and malformed responses carry no permanent signal.
        is GraphQLClientException.Transport,
        is GraphQLClientException.Decoding,
        is GraphQLClientException.MissingData -> true
    }

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
