package com.launchdarkly.observability.replay

import com.launchdarkly.observability.replay.capture.CaptureEvent
import com.launchdarkly.observability.replay.exporter.IdentifyItemPayload
import com.launchdarkly.observability.replay.exporter.ImageItemPayload
import com.launchdarkly.observability.replay.exporter.SessionReplayExporter
import com.launchdarkly.observability.replay.exporter.SessionReplayApiService
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItem
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled

class SessionReplayExporterTest {

    private lateinit var mockService: SessionReplayApiService
    private lateinit var exporter: SessionReplayExporter

    private val identifyEvent = IdentifyItemPayload(
        attributes = mapOf("key" to "user"),
        timestamp = 0L,
        sessionId = null
    )

    @BeforeEach
    fun setUp() {
        mockService = mockk<SessionReplayApiService>(relaxed = true)
        exporter = SessionReplayExporter(
            organizationVerboseId = "test-org",
            backendUrl = "http://test.com",
            serviceName = "test-service",
            serviceVersion = "1.0.0",
            injectedReplayApiService = mockService,
            canvasBufferLimit = 20,
            canvasDrawEntourage = 1,
            initialIdentifyItemPayload = identifyEvent,
            logger = mockk()
        )
    }

    @Test
    fun `constructor with mock service should use injected service`() = runTest {
        // Create a mock service
        val mockService = mockk<SessionReplayApiService>(relaxed = true)

        // Create the exporter with the mock service
        val exporter = SessionReplayExporter(
            organizationVerboseId = "test-org",
            backendUrl = "http://test.com",
            serviceName = "test-service",
            serviceVersion = "1.0.0",
            initialIdentifyItemPayload = identifyEvent,
            injectedReplayApiService = mockService,
            logger = mockk()
        )

        // Verify the exporter was created successfully
        assertNotNull(exporter)
    }

    @Test
    fun `constructor without mock service should create default service`() = runTest {
        // Create the exporter without injecting a service (should create default)
        val exporter = SessionReplayExporter(
            organizationVerboseId = "test-org",
            backendUrl = "http://test.com",
            serviceName = "test-service",
            serviceVersion = "1.0.0",
            initialIdentifyItemPayload = identifyEvent,
            logger = mockk()
        )

        // Verify the exporter was created successfully
        assertNotNull(exporter)
    }

    @Test
    fun `sendIdentifyAndCache should call identifyReplaySession`() = runTest {
        val updatedIdentify = IdentifyItemPayload(
            attributes = mapOf("key" to "updated-user"),
            timestamp = 1L,
            sessionId = "session-a"
        )

        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } just Runs

        exporter.sendIdentifyAndCache(updatedIdentify)

        coVerify(exactly = 1) { mockService.identifyReplaySession("session-a", updatedIdentify) }
    }

    @Test
    fun `export uses cached identify payload`() = runTest {
        val cachedIdentify = IdentifyItemPayload(
            attributes = mapOf("key" to "cached-user"),
            timestamp = 2L,
            sessionId = "session-a"
        )
        exporter.cacheIdentify(cachedIdentify)

        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a")
        )
        val items = createItemsFromCaptures(captureEvents)

        val payloadSlot = slot<IdentifyItemPayload>()
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any<String>(), capture(payloadSlot)) } just Runs
        coEvery { mockService.pushPayload(any(), any(), any()) } just Runs

        exporter.export(items)

        coVerify(exactly = 1) { mockService.identifyReplaySession("session-a", any<IdentifyItemPayload>()) }
        assertEquals(cachedIdentify, payloadSlot.captured)
    }

    @Test
    @Disabled // Feature of handling multiples session is not done
    fun `export should send full capture for first session and incremental for subsequent captures in same session`() = runTest {
        // Arrange: Create captures for two different sessions
        val sessionACaptureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 800, 600, 2000L, "session-a"),
            CaptureEvent("base64data3", 800, 600, 3000L, "session-a")
        )

        val sessionBCaptureEvents = listOf(
            CaptureEvent("base64data4", 1024, 768, 4000L, "session-b"),
            CaptureEvent("base64data5", 1024, 768, 5000L, "session-b")
        )

        val allCaptures = sessionACaptureEvents + sessionBCaptureEvents
        val items = createItemsFromCaptures(allCaptures)

        // Capture the events sent to pushPayload
        val capturedEvents = mutableListOf<List<Event>>()

        // Mock the API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), capture(capturedEvents)) } just Runs

        // Act: Export all items
        exporter.export(items)

        // Verify full capture calls for session A (first capture only)
        coVerify(exactly = 1) {
            mockService.initializeReplaySession("test-org", "session-a")
        }
        coVerify(exactly = 1) { mockService.identifyReplaySession(eq("session-a"), any<IdentifyItemPayload>()) }

        // Verify full capture calls for session B (first capture only)
        coVerify(exactly = 1) {
            mockService.initializeReplaySession("test-org", "session-b")
        }
        coVerify(exactly = 1) { mockService.identifyReplaySession(eq("session-b"), any<IdentifyItemPayload>()) }

        // Verify pushPayload is called for all captures (3 for session A + 2 for session B = 5 total)
        coVerify(exactly = 5) {
            mockService.pushPayload(any(), any(), any())
        }

        // Verify event types: First capture should be full (3 events), subsequent should be incremental (2 events each)
        assertEquals(5, capturedEvents.size)

        // Session A: First capture (full) + 2 incremental captures
        verifyFullCaptureEvents(capturedEvents[0]) // First capture should be full
        verifyIncrementalCaptureEvents(capturedEvents[1]) // Second capture should be incremental
        verifyIncrementalCaptureEvents(capturedEvents[2]) // Third capture should be incremental

        // Session B: First capture (full) + 1 incremental capture
        verifyFullCaptureEvents(capturedEvents[3]) // First capture should be full
        verifyIncrementalCaptureEvents(capturedEvents[4]) // Second capture should be incremental
    }

    @Test
    fun `export should send full capture when dimensions change within same session`() = runTest {
        // Arrange: Create captures for same session but with dimension changes
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),  // First capture - full
            CaptureEvent("base64data2", 800, 600, 2000L, "session-a"),  // Same dimensions - incremental
            CaptureEvent("base64data3", 1024, 768, 3000L, "session-a"), // Dimension change - full
            CaptureEvent(
                "base64data4",
                1024,
                768,
                4000L,
                "session-a"
            )  // Same dimensions - incremental
        )

        val items = createItemsFromCaptures(captureEvents)

        // Capture the events sent to pushPayload
        val capturedEventsLists = mutableListOf<List<Event>>()

        // Mock the API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), capture(capturedEventsLists)) } just Runs

        // Act: Export all items
        exporter.export(items)

        // Verify initializeReplaySession is called twice (first capture + dimension change)
        coVerify(exactly = 1) {
            mockService.initializeReplaySession("test-org", "session-a")
        }

        // Verify identifyReplaySession is called twice (first capture + dimension change)
        coVerify(exactly = 1) { mockService.identifyReplaySession(eq("session-a"), any<IdentifyItemPayload>()) }

        // Verify pushPayload is called for all captures
        coVerify(exactly = 1) {
            mockService.pushPayload("session-a", any(), any())
        }

        // Verify event types: First and third captures should be full, second and fourth should be incremental
        val capturedEvents: List<Event> = capturedEventsLists[0]
        verifyFullCaptureEvents(capturedEvents) // First capture - full
        verifyIncrementalCaptureEvents(capturedEvents) // Second capture - incremental
    }

    @Test
    fun `test canvas buffer limit`() = runTest {
        // Arrange: Create captures for same session but with dimension changes
        val captureEvents = listOf(
            // small canvas
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),  // First capture - full
            // large canvases to cause overlimit
            CaptureEvent("base64data2222222222222", 800, 600, 2000L, "session-a"),  // Same dimensions - incremental
            CaptureEvent("base64data3333333333333", 1024, 768, 3000L, "session-a"), // Dimension change - full
            CaptureEvent(
                "base64data444444444444",
                1024,
                768,
                4000L,
                "session-a"
            )  // Same dimensions - incremental
        )

        val items = createItemsFromCaptures(captureEvents)

        // Capture the events sent to pushPayload
        val capturedEventsLists = mutableListOf<List<Event>>()

        // Mock the API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), capture(capturedEventsLists)) } just Runs

        // Act: Export all items
        exporter.export(items)

        // Verify event types: First and third captures should be full, second and fourth should be incremental
        val capturedEvents: List<Event> = capturedEventsLists[0]
        verifyFullCaptureEvents(capturedEvents, count = 3) // First capture - full
        verifyIncrementalCaptureEvents(capturedEvents, 1) // Second capture - incremental
    }

    @Test
    fun `export should ignore unsupported payloads`() = runTest {
        // Arrange: Create mix of valid and unsupported payloads
        val validCaptureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 800, 600, 2000L, "session-a")
        )

        val validItems = createItemsFromCaptures(validCaptureEvents)
        val allItems = validItems + EventQueueItem(UnknownPayload())

        // Mock the API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), any()) } just Runs

        // Act: Export all items
        exporter.export(allItems)

        // Verify only valid captures are processed
        coVerify(exactly = 1) {
            mockService.initializeReplaySession("test-org", "session-a")
        }
        coVerify(exactly = 1) { mockService.identifyReplaySession(eq("session-a"), any<IdentifyItemPayload>()) }
        coVerify(exactly = 1) {
            mockService.pushPayload("session-a", any(), any())
        }
    }

    @Test
    fun `export should handle empty item collection`() = runTest {
        // Act: Export empty collection
        exporter.export(emptyList())

        // Verify no API calls are made
        coVerify(exactly = 0) { mockService.initializeReplaySession(any(), any()) }
        coVerify(exactly = 0) { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) }
        coVerify(exactly = 0) { mockService.pushPayload(any(), any(), any()) }
    }

    @Test
    fun `export should handle API service failures gracefully`() = runTest {
        // Arrange: Create a single capture to test basic failure handling
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a")
        )
        val items = createItemsFromCaptures(captureEvents)

        // Mock API service to throw exceptions
        coEvery { mockService.initializeReplaySession(any(), any()) } throws RuntimeException("Network error")
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } throws RuntimeException("Authentication failed")
        coEvery { mockService.pushPayload(any(), any(), any()) } throws RuntimeException("Server error")

        // Act: Export items
        var thrown: Throwable? = null
        try {
            exporter.export(items)
        } catch (e: Exception) {
            thrown = e
        }

        // Assert: Verify the export fails due to API errors
        assertNotNull(thrown)

        // Verify API methods were called despite failures
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 0) { mockService.identifyReplaySession(eq("session-a"), any<IdentifyItemPayload>()) }
        coVerify(exactly = 0) { mockService.pushPayload("session-a", any(), any()) }
    }

    @Test
    fun `export should handle multiple captures in same session with proper state tracking`() = runTest {
        // Arrange: Create two captures with same session and dimensions
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 800, 600, 2000L, "session-a")
        )
        val items = createItemsFromCaptures(captureEvents)

        // Mock API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), any()) } just Runs

        // Act: Export items
        exporter.export(items)

        // Verify API calls: First capture should be full, second should be incremental
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 1) { mockService.identifyReplaySession(eq("session-a"), any<IdentifyItemPayload>()) }
        coVerify(exactly = 1) { mockService.pushPayload("session-a", any(), any()) }
    }

    @Test
    @Disabled // Handling exceptions in initializeReplaySession and identifyReplaySession not done
    fun `export should stop processing on first failure and not process remaining captures`() = runTest {
        // Arrange: Create captures for two different sessions
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 1024, 768, 2000L, "session-b")
        )
        val items = createItemsFromCaptures(captureEvents)

        // Mock API service: first session succeeds, second session fails
        coEvery { mockService.initializeReplaySession("test-org", "session-a") } just Runs
        coEvery { mockService.identifyReplaySession(eq("session-a"), any<IdentifyItemPayload>()) } just Runs
        coEvery { mockService.pushPayload("session-a", any(), any()) } just Runs

        coEvery { mockService.initializeReplaySession("test-org", "session-b") } throws RuntimeException("Network timeout")
        coEvery { mockService.identifyReplaySession(eq("session-b"), any<IdentifyItemPayload>()) } throws RuntimeException("Network timeout")
        coEvery { mockService.pushPayload("session-b", any(), any()) } throws RuntimeException("Network timeout")

        // Act: Export items
        var thrown: Throwable? = null
        try {
            exporter.export(items)
        } catch (e: Exception) {
            thrown = e
        }

        // Assert: Verify the export fails due to first failure
        assertNotNull(thrown)

        // Verify only first session was processed (second session should not be processed due to early termination)
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 1) { mockService.identifyReplaySession(eq("session-a"), any<IdentifyItemPayload>()) }
        coVerify(exactly = 1) { mockService.pushPayload("session-a", any(), any()) }

        // Verify second session was never processed
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-b") }
        coVerify(exactly = 0) { mockService.identifyReplaySession(eq("session-b"), any<IdentifyItemPayload>()) }
        coVerify(exactly = 0) { mockService.pushPayload("session-b", any(), any()) }
    }

    @Test
    fun `export should handle pushPayload failure after successful initialization`() = runTest {
        // Arrange: Create a single capture
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a")
        )
        val items = createItemsFromCaptures(captureEvents)

        // Mock API service: initialization succeeds but pushPayload fails
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), any()) } throws RuntimeException("Payload too large")

        // Act: Export items
        var thrown: Throwable? = null
        try {
            exporter.export(items)
        } catch (e: Exception) {
            thrown = e
        }

        // Assert: Verify the export fails due to pushPayload failure
        assertNotNull(thrown)

        // Verify all API methods were called
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 1) { mockService.identifyReplaySession(eq("session-a"), any<IdentifyItemPayload>()) }
        coVerify(exactly = 1) { mockService.pushPayload("session-a", any(), any()) }
    }

    @Test
    fun `export should stop processing when first capture fails in same session`() = runTest {
        // Arrange: Create two captures with same session and dimensions
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 800, 600, 2000L, "session-a")
        )
        val items = createItemsFromCaptures(captureEvents)

        // Mock API service: first capture fails, second should not be processed
        coEvery { mockService.initializeReplaySession(any(), any()) } throws RuntimeException("Network error")
        coEvery { mockService.identifyReplaySession(any<String>(), any<IdentifyItemPayload>()) } throws RuntimeException("Authentication failed")
        coEvery { mockService.pushPayload(any(), any(), any()) } throws RuntimeException("Server error")

        // Act: Export items
        var thrown: Throwable? = null
        try {
            exporter.export(items)
        } catch (e: Exception) {
            thrown = e
        }

        // Assert: Verify the export fails due to first capture failure
        assertNotNull(thrown)

        // Verify only first capture was attempted (second should not be processed due to early termination)
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 0) {
            mockService.identifyReplaySession(
                eq("session-a"),
                any<IdentifyItemPayload>()
            )
        } // Should not be called due to initializeReplaySession failure
        coVerify(exactly = 0) { mockService.pushPayload("session-a", any(), any()) } // Should not be called due to initializeReplaySession failure
    }

    // Helper functions

    /**
     * Creates a list of EventQueueItem from a list of Capture objects
     */
    private fun createItemsFromCaptures(captureEvents: List<CaptureEvent>): List<EventQueueItem> {
        return captureEvents.map { capture ->
            EventQueueItem(ImageItemPayload(capture))
        }
    }

    private class UnknownPayload(
        override val timestamp: Long = System.currentTimeMillis(),
    ) : EventQueueItemPayload {
        override val exporterClass: Class<out EventExporting>
            get() = SessionReplayExporter::class.java

        override fun cost(): Int = 1
    }

    /**
     * Verifies that the events represent a full capture (META, FULL_SNAPSHOT, CUSTOM)
     */
    private fun verifyFullCaptureEvents(events: List<Event>, count: Int = 2) {
        // Verify META event
        val metaEvent = events.find { it.type == EventType.META }
        assertNotNull(metaEvent, "Full capture should contain a META event")

        // Verify FULL_SNAPSHOT event
        val fullSnapshotEvents = events.filter { it.type == EventType.FULL_SNAPSHOT }
        assertEquals(count, fullSnapshotEvents.size, "Full capture should contain $count FULL_SNAPSHOT events")

        // Verify CUSTOM event (viewport)
        val customEvent = events.find { it.type == EventType.CUSTOM }
        assertNotNull(customEvent, "Full capture should contain a CUSTOM event")
    }

    /**
     * Verifies that the events represent an incremental capture (2 INCREMENTAL_SNAPSHOT events)
     */
    private fun verifyIncrementalCaptureEvents(events: List<Event>, count: Int = 2) {
        // Verify both events are INCREMENTAL_SNAPSHOT
        val incrementalEvents = events.filter { it.type == EventType.INCREMENTAL_SNAPSHOT }
        assertEquals(count, incrementalEvents.size, "Incremental capture should contain $count INCREMENTAL_SNAPSHOT events")
    }
}
