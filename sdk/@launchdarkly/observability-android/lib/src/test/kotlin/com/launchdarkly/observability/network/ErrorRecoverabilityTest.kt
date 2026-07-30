package com.launchdarkly.observability.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrorRecoverabilityTest {

    @Test
    fun `unauthorized statuses are unrecoverable`() {
        listOf(401, 402, 403, 404, 405, 409, 410, 418, 451).forEach { status ->
            assertFalse(ErrorRecoverability.isHttpErrorRecoverable(status), "status $status")
        }
    }

    @Test
    fun `bad request timeout and rate limit are recoverable`() {
        listOf(400, 408, 429).forEach { status ->
            assertTrue(ErrorRecoverability.isHttpErrorRecoverable(status), "status $status")
        }
    }

    @Test
    fun `server errors are recoverable`() {
        listOf(500, 502, 503, 504).forEach { status ->
            assertTrue(ErrorRecoverability.isHttpErrorRecoverable(status), "status $status")
        }
    }

    @Test
    fun `non error statuses are recoverable`() {
        listOf(200, 204, 301, 302).forEach { status ->
            assertTrue(ErrorRecoverability.isHttpErrorRecoverable(status), "status $status")
        }
    }

    @Test
    fun `graph errors on a 200 are unrecoverable`() {
        val response = response(statusCode = 200, errors = listOf(GraphQLError(message = "session replay blocked")))

        assertFalse(ErrorRecoverability.isErrorRecoverable(response))
    }

    @Test
    fun `a retryable graph error is recoverable despite the 200`() {
        val response = response(
            statusCode = 200,
            errors = listOf(GraphQLError(message = "try later", extensions = GraphQLErrorExtensions(retryable = true))),
        )

        assertTrue(ErrorRecoverability.isErrorRecoverable(response))
    }

    @Test
    fun `a non retryable graph error wins over a recoverable status`() {
        val response = response(
            statusCode = 429,
            errors = listOf(
                GraphQLError(
                    message = "blocked in region",
                    extensions = GraphQLErrorExtensions(code = "SESSION_REPLAY_BLOCKED_IN_REGION", retryable = false),
                )
            ),
        )

        assertFalse(ErrorRecoverability.isErrorRecoverable(response))
    }

    @Test
    fun `a retryable graph error wins over an unrecoverable status`() {
        val response = response(
            statusCode = 403,
            errors = listOf(GraphQLError(message = "expired token", extensions = GraphQLErrorExtensions(retryable = true))),
        )

        assertTrue(ErrorRecoverability.isErrorRecoverable(response))
    }

    @Test
    fun `one non retryable error makes the whole response unrecoverable`() {
        val response = response(
            statusCode = 200,
            errors = listOf(
                GraphQLError(message = "try later", extensions = GraphQLErrorExtensions(retryable = true)),
                GraphQLError(message = "blocked", extensions = GraphQLErrorExtensions(retryable = false)),
            ),
        )

        assertFalse(ErrorRecoverability.isErrorRecoverable(response))
    }

    @Test
    fun `errors without extensions fall back to the status code`() {
        val errors = listOf(GraphQLError(message = "rejected"))

        assertFalse(ErrorRecoverability.isErrorRecoverable(response(statusCode = 403, errors = errors)))
        assertTrue(ErrorRecoverability.isErrorRecoverable(response(statusCode = 503, errors = errors)))
    }

    @Test
    fun `a failure without a status is recoverable`() {
        val response = response(statusCode = null, errors = listOf(GraphQLError(message = "timeout")))

        assertTrue(ErrorRecoverability.isErrorRecoverable(response))
    }

    @Test
    fun `a classified failure keeps its verdict`() {
        assertFalse(ErrorRecoverability.isErrorRecoverable(ClassifiedFailure(isRecoverable = false)))
        assertTrue(ErrorRecoverability.isErrorRecoverable(ClassifiedFailure(isRecoverable = true)))
    }

    @Test
    fun `an unclassified failure is recoverable`() {
        assertTrue(ErrorRecoverability.isErrorRecoverable(IllegalStateException("something went wrong")))
    }

    private fun response(statusCode: Int?, errors: List<GraphQLError>) =
        GraphQLResponse<String>(data = null, errors = errors, httpStatusCode = statusCode)

    private class ClassifiedFailure(override val isRecoverable: Boolean) : RuntimeException(), RecoverableFailure
}
