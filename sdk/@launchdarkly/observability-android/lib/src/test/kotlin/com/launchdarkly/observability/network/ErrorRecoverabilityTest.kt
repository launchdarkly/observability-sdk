package com.launchdarkly.observability.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

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
    fun `graph errors are unrecoverable`() {
        val failure = GraphQLClientException.GraphQLErrors(listOf(GraphQLError(message = "session replay blocked")))

        assertFalse(ErrorRecoverability.isErrorRecoverable(failure))
    }

    @Test
    fun `a retryable graph error is recoverable`() {
        val failure = GraphQLClientException.GraphQLErrors(
            listOf(GraphQLError(message = "try later", extensions = GraphQLErrorExtensions(retryable = true)))
        )

        assertTrue(ErrorRecoverability.isErrorRecoverable(failure))
    }

    @Test
    fun `a non retryable graph error wins over a recoverable status`() {
        val failure = GraphQLClientException.HttpStatus(
            statusCode = 429,
            body = "rejected",
            errors = listOf(
                GraphQLError(
                    message = "blocked in region",
                    extensions = GraphQLErrorExtensions(code = "SESSION_REPLAY_BLOCKED_IN_REGION", retryable = false),
                )
            ),
        )

        assertFalse(ErrorRecoverability.isErrorRecoverable(failure))
    }

    @Test
    fun `a retryable graph error wins over an unrecoverable status`() {
        val failure = GraphQLClientException.HttpStatus(
            statusCode = 403,
            body = "rejected",
            errors = listOf(GraphQLError(message = "expired token", extensions = GraphQLErrorExtensions(retryable = true))),
        )

        assertTrue(ErrorRecoverability.isErrorRecoverable(failure))
    }

    @Test
    fun `one non retryable error makes the whole failure unrecoverable`() {
        val failure = GraphQLClientException.GraphQLErrors(
            listOf(
                GraphQLError(message = "try later", extensions = GraphQLErrorExtensions(retryable = true)),
                GraphQLError(message = "blocked", extensions = GraphQLErrorExtensions(retryable = false)),
            )
        )

        assertFalse(ErrorRecoverability.isErrorRecoverable(failure))
    }

    @Test
    fun `a rejection without extensions falls back to the status code`() {
        val errors = listOf(GraphQLError(message = "rejected"))

        assertFalse(ErrorRecoverability.isErrorRecoverable(GraphQLClientException.HttpStatus(403, "rejected", errors)))
        assertTrue(ErrorRecoverability.isErrorRecoverable(GraphQLClientException.HttpStatus(503, "rejected", errors)))
    }

    @Test
    fun `a rejection with no GraphQL body is classified by its status`() {
        assertFalse(ErrorRecoverability.isErrorRecoverable(GraphQLClientException.HttpStatus(403, "Forbidden")))
        assertTrue(ErrorRecoverability.isErrorRecoverable(GraphQLClientException.HttpStatus(429, "Slow down")))
    }

    @Test
    fun `failures that never reached a status are recoverable`() {
        assertTrue(ErrorRecoverability.isErrorRecoverable(GraphQLClientException.Transport(IOException("timeout"))))
        assertTrue(ErrorRecoverability.isErrorRecoverable(GraphQLClientException.Decoding(IOException("bad shape"))))
        assertTrue(ErrorRecoverability.isErrorRecoverable(GraphQLClientException.MissingData()))
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

    private class ClassifiedFailure(override val isRecoverable: Boolean) : RuntimeException(), RecoverableFailure
}
