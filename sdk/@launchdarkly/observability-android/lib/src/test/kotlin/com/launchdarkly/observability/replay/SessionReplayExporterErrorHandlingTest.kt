package com.launchdarkly.observability.replay

import com.launchdarkly.observability.network.GraphQLClientException
import com.launchdarkly.observability.network.GraphQLError
import com.launchdarkly.observability.network.GraphQLErrorExtensions
import com.launchdarkly.observability.replay.capture.ExportFrame
import com.launchdarkly.observability.replay.exporter.IdentifyItemPayload
import com.launchdarkly.observability.replay.exporter.ImageItemPayload
import com.launchdarkly.observability.replay.exporter.SessionReplayApiException
import com.launchdarkly.observability.replay.exporter.SessionReplayApiService
import com.launchdarkly.observability.replay.exporter.SessionReplayExporter
import com.launchdarkly.observability.replay.transport.EventQueueItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * How the exporter classifies failed session initialization and payload pushes, and what it reports back
 * to the service.
 */
class SessionReplayExporterErrorHandlingTest {

    private lateinit var mockService: SessionReplayApiService
    private lateinit var exporter: SessionReplayExporter
    private val verdicts = mutableListOf<SessionReplayInitializationVerdict>()

    @BeforeEach
    fun setUp() {
        verdicts.clear()
        mockService = mockk(relaxed = true)
        exporter = SessionReplayExporter(
            organizationVerboseId = "test-org",
            backendUrl = "http://test.com",
            serviceName = "test-service",
            serviceVersion = "1.0.0",
            initialIdentifyItemPayload = IdentifyItemPayload(
                attributes = mapOf("key" to "user"),
                timestamp = 0L,
                sessionId = null,
            ),
            title = "test-app",
            injectedReplayApiService = mockService,
            logger = mockk(relaxed = true),
            onInitializationVerdict = { verdicts.add(it) },
        )
    }

    @Test
    fun `an accepted session is reported as allowed`() = runTest {
        exporter.export(captureItems("session-a"))

        assertEquals(listOf(SessionReplayInitializationVerdict.Allowed), verdicts)
    }

    @Test
    fun `prepareSession initializes without a capture, and the export does not repeat it`() = runTest {
        exporter.prepareSession("session-a")

        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        assertEquals(listOf(SessionReplayInitializationVerdict.Allowed), verdicts)

        exporter.export(captureItems("session-a"))

        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
    }

    @Test
    fun `an unrecoverable initialization failure stops further exports`() = runTest {
        coEvery { mockService.initializeReplaySession(any(), any()) } throws refusal("initializeReplaySession")

        assertNotNull(exportFailure(captureItems("session-a")))
        assertEquals(
            listOf(
                SessionReplayInitializationVerdict.Unrecoverable(
                    "initializeReplaySession failed: GraphQL errors: blocked in region"
                )
            ),
            verdicts
        )

        // Recording is over for this launch: the next batch is dropped rather than retried, so the queue
        // drains instead of filling up.
        assertNull(exportFailure(captureItems("session-a")))
        coVerify(exactly = 1) { mockService.initializeReplaySession(any(), any()) }
    }

    @Test
    fun `an unrecoverable push failure stops further exports`() = runTest {
        coEvery { mockService.pushPayload(any(), any(), any()) } throws refusal("pushPayload")

        assertNotNull(exportFailure(captureItems("session-a")))
        assertEquals(1, verdicts.count { it is SessionReplayInitializationVerdict.Unrecoverable })

        assertNull(exportFailure(captureItems("session-a")))
        coVerify(exactly = 1) { mockService.pushPayload(any(), any(), any()) }
    }

    @Test
    fun `a recoverable failure is retried and never reported`() = runTest {
        coEvery { mockService.initializeReplaySession(any(), any()) } throws
            transientFailure("initializeReplaySession")

        assertNotNull(exportFailure(captureItems("session-a")))
        assertNotNull(exportFailure(captureItems("session-a")))

        // Both batches were attempted, and the failures stay with the export retry loop.
        coVerify(exactly = 2) { mockService.initializeReplaySession(any(), any()) }
        assertTrue(verdicts.isEmpty())
    }

    @Test
    fun `a failure of unknown origin is treated as recoverable`() = runTest {
        coEvery { mockService.initializeReplaySession(any(), any()) } throws RuntimeException("socket closed")

        assertNotNull(exportFailure(captureItems("session-a")))
        assertNotNull(exportFailure(captureItems("session-a")))

        coVerify(exactly = 2) { mockService.initializeReplaySession(any(), any()) }
        assertTrue(verdicts.isEmpty())
    }

    @Test
    fun `prepareSession reports a refusal without throwing`() = runTest {
        coEvery { mockService.initializeReplaySession(any(), any()) } throws
            SessionReplayApiException("initializeReplaySession", GraphQLClientException.HttpStatus(403, "Forbidden"))

        exporter.prepareSession("session-a")

        assertEquals(
            listOf(
                SessionReplayInitializationVerdict.Unrecoverable(
                    "initializeReplaySession failed: HTTP Error 403: Forbidden"
                )
            ),
            verdicts
        )
    }

    @Test
    fun `a transient identify failure does not withhold recording`() = runTest {
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } throws
            transientFailure("identifyReplaySession")

        assertNotNull(exportFailure(captureItems("session-a")))

        // The session itself was accepted, so screenshots start even though identify has to be retried.
        assertEquals(listOf(SessionReplayInitializationVerdict.Allowed), verdicts)
    }

    /** A refusal the backend cannot be retried out of: a GraphQL error marked non-retryable. */
    private fun refusal(operation: String) = SessionReplayApiException(
        operation,
        GraphQLClientException.GraphQLErrors(
            listOf(
                GraphQLError(
                    message = "blocked in region",
                    extensions = GraphQLErrorExtensions(code = "SESSION_REPLAY_BLOCKED_IN_REGION", retryable = false),
                )
            )
        ),
    )

    private fun transientFailure(operation: String) =
        SessionReplayApiException(operation, GraphQLClientException.HttpStatus(429, "Slow down"))

    private fun captureItems(sessionId: String): List<EventQueueItem> =
        listOf(EventQueueItem(ImageItemPayload(ExportFrame("base64data", 800, 600, 1000L, sessionId))))

    /** The exception [SessionReplayExporter.export] threw, or `null` when it succeeded. */
    private suspend fun exportFailure(items: List<EventQueueItem>): Throwable? = try {
        exporter.export(items)
        null
    } catch (e: Exception) {
        e
    }
}
