package com.launchdarkly.observability.replay

import io.mockk.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.data.LogRecordData
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit

class RRwebGraphQLReplayLogExporterTest {

    private lateinit var mockService: SessionReplayApiService
    private lateinit var exporter: RRwebGraphQLReplayLogExporter

    @BeforeEach
    fun setUp() {
        mockService = mockk<SessionReplayApiService>(relaxed = true)
        exporter = RRwebGraphQLReplayLogExporter(
            organizationVerboseId = "test-org",
            backendUrl = "http://test.com",
            serviceName = "test-service",
            serviceVersion = "1.0.0",
            injectedReplayApiService = mockService
        )
    }

    @Test
    fun `constructor with mock service should use injected service`() = runTest {
        // Create a mock service
        val mockService = mockk<SessionReplayApiService>(relaxed = true)
        
        // Create the exporter with the mock service
        val exporter = RRwebGraphQLReplayLogExporter(
            organizationVerboseId = "test-org",
            backendUrl = "http://test.com",
            serviceName = "test-service",
            serviceVersion = "1.0.0",
            injectedReplayApiService = mockService
        )
        
        // Verify the exporter was created successfully
        assertNotNull(exporter)
    }

    @Test
    fun `constructor without mock service should create default service`() = runTest {
        // Create the exporter without injecting a service (should create default)
        val exporter = RRwebGraphQLReplayLogExporter(
            organizationVerboseId = "test-org",
            backendUrl = "http://test.com",
            serviceName = "test-service",
            serviceVersion = "1.0.0"
        )
        
        // Verify the exporter was created successfully
        assertNotNull(exporter)
    }

    @Test
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
        val logRecords = createLogRecordsFromCaptures(allCaptures)
        
        // Capture the events sent to pushPayload
        val capturedEvents = mutableListOf<List<Event>>()
        
        // Mock the API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), capture(capturedEvents)) } just Runs
        
        // Act: Export all log records
        val result = exporter.export(logRecords.toMutableList())
        
        // Assert: Verify the result completes successfully
        assertTrue(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify full capture calls for session A (first capture only)
        coVerify(exactly = 1) { 
            mockService.initializeReplaySession("test-org", "session-a") 
        }
        coVerify(exactly = 1) { 
            mockService.identifyReplaySession("session-a") 
        }
        
        // Verify full capture calls for session B (first capture only)
        coVerify(exactly = 1) { 
            mockService.initializeReplaySession("test-org", "session-b") 
        }
        coVerify(exactly = 1) { 
            mockService.identifyReplaySession("session-b") 
        }
        
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
            CaptureEvent("base64data4", 1024, 768, 4000L, "session-a")  // Same dimensions - incremental
        )
        
        val logRecords = createLogRecordsFromCaptures(captureEvents)
        
        // Capture the events sent to pushPayload
        val capturedEvents = mutableListOf<List<Event>>()
        
        // Mock the API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), capture(capturedEvents)) } just Runs
        
        // Act: Export all log records
        val result = exporter.export(logRecords.toMutableList())
        
        // Assert: Verify the result completes successfully
        assertTrue(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify initializeReplaySession is called twice (first capture + dimension change)
        coVerify(exactly = 2) { 
            mockService.initializeReplaySession("test-org", "session-a") 
        }
        
        // Verify identifyReplaySession is called twice (first capture + dimension change)
        coVerify(exactly = 2) { 
            mockService.identifyReplaySession("session-a") 
        }
        
        // Verify pushPayload is called for all captures
        coVerify(exactly = 4) { 
            mockService.pushPayload("session-a", any(), any()) 
        }
        
        // Verify event types: First and third captures should be full, second and fourth should be incremental
        assertEquals(4, capturedEvents.size)
        verifyFullCaptureEvents(capturedEvents[0]) // First capture - full
        verifyIncrementalCaptureEvents(capturedEvents[1]) // Second capture - incremental
        verifyFullCaptureEvents(capturedEvents[2]) // Third capture - full (dimension change)
        verifyIncrementalCaptureEvents(capturedEvents[3]) // Fourth capture - incremental
    }

    @Test
    fun `export should handle mixed valid and invalid log records`() = runTest {
        // Arrange: Create mix of valid and invalid log records
        val validCaptureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 800, 600, 2000L, "session-a")
        )
        
        val validLogRecords = createLogRecordsFromCaptures(validCaptureEvents)
        val invalidLogRecords = listOf(
            createLogRecordWithAttributes(
                eventDomain = "invalid-domain", // Wrong domain
                imageWidth = 800L,
                imageHeight = 600L,
                imageData = "base64data",
                sessionId = "session-a"
            ),
            createLogRecordWithAttributes(
                eventDomain = "media",
                imageWidth = null, // Missing width
                imageHeight = 600L,
                imageData = "base64data",
                sessionId = "session-a"
            )
        )
        
        val allLogRecords = validLogRecords + invalidLogRecords
        
        // Mock the API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), any()) } just Runs
        
        // Act: Export all log records
        val result = exporter.export(allLogRecords.toMutableList())
        
        // Assert: Verify the result completes successfully
        assertTrue(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify only valid captures are processed
        coVerify(exactly = 1) { 
            mockService.initializeReplaySession("test-org", "session-a") 
        }
        coVerify(exactly = 1) { 
            mockService.identifyReplaySession("session-a") 
        }
        coVerify(exactly = 2) { 
            mockService.pushPayload("session-a", any(), any()) 
        }
    }

    @Test
    fun `export should handle empty log collection`() = runTest {
        // Act: Export empty collection
        val result = exporter.export(mutableListOf())
        
        // Assert: Verify the result completes successfully
        assertTrue(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify no API calls are made
        coVerify(exactly = 0) { mockService.initializeReplaySession(any(), any()) }
        coVerify(exactly = 0) { mockService.identifyReplaySession(any()) }
        coVerify(exactly = 0) { mockService.pushPayload(any(), any(), any()) }
    }

    @Test
    fun `export should handle API service failures gracefully`() = runTest {
        // Arrange: Create a single capture to test basic failure handling
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a")
        )
        val logRecords = createLogRecordsFromCaptures(captureEvents)
        
        // Mock API service to throw exceptions
        coEvery { mockService.initializeReplaySession(any(), any()) } throws RuntimeException("Network error")
        coEvery { mockService.identifyReplaySession(any()) } throws RuntimeException("Authentication failed")
        coEvery { mockService.pushPayload(any(), any(), any()) } throws RuntimeException("Server error")
        
        // Act: Export log records
        val result = exporter.export(logRecords.toMutableList())
        
        // Assert: Verify the result fails due to API errors
        assertFalse(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify API methods were called despite failures
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 0) { mockService.identifyReplaySession("session-a") }
        coVerify(exactly = 0) { mockService.pushPayload("session-a", any(), any()) }
    }

    @Test
    fun `export should handle multiple captures in same session with proper state tracking`() = runTest {
        // Arrange: Create two captures with same session and dimensions
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 800, 600, 2000L, "session-a")
        )
        val logRecords = createLogRecordsFromCaptures(captureEvents)
        
        // Mock API service methods
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), any()) } just Runs
        
        // Act: Export log records
        val result = exporter.export(logRecords.toMutableList())
        
        // Assert: Verify the result completes successfully
        assertTrue(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify API calls: First capture should be full, second should be incremental
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 1) { mockService.identifyReplaySession("session-a") }
        coVerify(exactly = 2) { mockService.pushPayload("session-a", any(), any()) }
    }

    @Test
    fun `export should stop processing on first failure and not process remaining captures`() = runTest {
        // Arrange: Create captures for two different sessions
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 1024, 768, 2000L, "session-b")
        )
        val logRecords = createLogRecordsFromCaptures(captureEvents)
        
        // Mock API service: first session succeeds, second session fails
        coEvery { mockService.initializeReplaySession("test-org", "session-a") } just Runs
        coEvery { mockService.identifyReplaySession("session-a") } just Runs
        coEvery { mockService.pushPayload("session-a", any(), any()) } just Runs
        
        coEvery { mockService.initializeReplaySession("test-org", "session-b") } throws RuntimeException("Network timeout")
        coEvery { mockService.identifyReplaySession("session-b") } throws RuntimeException("Network timeout")
        coEvery { mockService.pushPayload("session-b", any(), any()) } throws RuntimeException("Network timeout")
        
        // Act: Export log records
        val result = exporter.export(logRecords.toMutableList())
        
        // Assert: Verify the result fails due to first failure
        assertFalse(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify only first session was processed (second session should not be processed due to early termination)
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 1) { mockService.identifyReplaySession("session-a") }
        coVerify(exactly = 1) { mockService.pushPayload("session-a", any(), any()) }
        
        // Verify second session was never processed
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-b") }
        coVerify(exactly = 0) { mockService.identifyReplaySession("session-b") }
        coVerify(exactly = 0) { mockService.pushPayload("session-b", any(), any()) }
    }

    @Test
    fun `export should handle pushPayload failure after successful initialization`() = runTest {
        // Arrange: Create a single capture
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a")
        )
        val logRecords = createLogRecordsFromCaptures(captureEvents)
        
        // Mock API service: initialization succeeds but pushPayload fails
        coEvery { mockService.initializeReplaySession(any(), any()) } just Runs
        coEvery { mockService.identifyReplaySession(any()) } just Runs
        coEvery { mockService.pushPayload(any(), any(), any()) } throws RuntimeException("Payload too large")
        
        // Act: Export log records
        val result = exporter.export(logRecords.toMutableList())
        
        // Assert: Verify the result fails due to pushPayload failure
        assertFalse(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify all API methods were called
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 1) { mockService.identifyReplaySession("session-a") }
        coVerify(exactly = 1) { mockService.pushPayload("session-a", any(), any()) }
    }

    @Test
    fun `export should stop processing when first capture fails in same session`() = runTest {
        // Arrange: Create two captures with same session and dimensions
        val captureEvents = listOf(
            CaptureEvent("base64data1", 800, 600, 1000L, "session-a"),
            CaptureEvent("base64data2", 800, 600, 2000L, "session-a")
        )
        val logRecords = createLogRecordsFromCaptures(captureEvents)
        
        // Mock API service: first capture fails, second should not be processed
        coEvery { mockService.initializeReplaySession(any(), any()) } throws RuntimeException("Network error")
        coEvery { mockService.identifyReplaySession(any()) } throws RuntimeException("Authentication failed")
        coEvery { mockService.pushPayload(any(), any(), any()) } throws RuntimeException("Server error")
        
        // Act: Export log records
        val result = exporter.export(logRecords.toMutableList())
        
        // Assert: Verify the result fails due to first capture failure
        assertFalse(result.join(5, TimeUnit.SECONDS).isSuccess)
        
        // Verify only first capture was attempted (second should not be processed due to early termination)
        coVerify(exactly = 1) { mockService.initializeReplaySession("test-org", "session-a") }
        coVerify(exactly = 0) { mockService.identifyReplaySession("session-a") } // Should not be called due to initializeReplaySession failure
        coVerify(exactly = 0) { mockService.pushPayload("session-a", any(), any()) } // Should not be called due to initializeReplaySession failure
    }

    // Helper functions

    /**
     * Creates a list of LogRecordData from a list of Capture objects
     */
    private fun createLogRecordsFromCaptures(captureEvents: List<CaptureEvent>): List<LogRecordData> {
        return captureEvents.map { capture ->
            createLogRecordWithAttributes(
                eventDomain = "media",
                imageWidth = capture.origWidth.toLong(),
                imageHeight = capture.origHeight.toLong(),
                imageData = capture.imageBase64,
                sessionId = capture.session,
                timestamp = capture.timestamp * 1_000_000 // Convert to nanoseconds
            )
        }
    }

    /**
     * Creates a LogRecordData with the specified attributes for testing
     */
    private fun createLogRecordWithAttributes(
        eventDomain: String?,
        imageWidth: Long?,
        imageHeight: Long?,
        imageData: String?,
        sessionId: String?,
        timestamp: Long = System.currentTimeMillis() * 1_000_000
    ): LogRecordData {
        val attributesBuilder = Attributes.builder()
        
        eventDomain?.let { attributesBuilder.put(AttributeKey.stringKey("event.domain"), it) }
        imageWidth?.let { attributesBuilder.put(AttributeKey.longKey("image.width"), it) }
        imageHeight?.let { attributesBuilder.put(AttributeKey.longKey("image.height"), it) }
        imageData?.let { attributesBuilder.put(AttributeKey.stringKey("image.data"), it) }
        sessionId?.let { attributesBuilder.put(AttributeKey.stringKey("session.id"), it) }
        
        return mockk<LogRecordData>().apply {
            every { getAttributes() } returns attributesBuilder.build()
            every { observedTimestampEpochNanos } returns timestamp
        }
    }

    /**
     * Verifies that the events represent a full capture (META, FULL_SNAPSHOT, CUSTOM)
     */
    private fun verifyFullCaptureEvents(events: List<Event>) {
        assertEquals(3, events.size, "Full capture should have exactly 3 events")
        
        // Verify META event
        val metaEvent = events.find { it.type == EventType.META }
        assertNotNull(metaEvent, "Full capture should contain a META event")
        
        // Verify FULL_SNAPSHOT event
        val fullSnapshotEvent = events.find { it.type == EventType.FULL_SNAPSHOT }
        assertNotNull(fullSnapshotEvent, "Full capture should contain a FULL_SNAPSHOT event")
        
        // Verify CUSTOM event (viewport)
        val customEvent = events.find { it.type == EventType.CUSTOM }
        assertNotNull(customEvent, "Full capture should contain a CUSTOM event")
    }

    /**
     * Verifies that the events represent an incremental capture (2 INCREMENTAL_SNAPSHOT events)
     */
    private fun verifyIncrementalCaptureEvents(events: List<Event>) {
        assertEquals(2, events.size, "Incremental capture should have exactly 2 events")
        
        // Verify both events are INCREMENTAL_SNAPSHOT
        val incrementalEvents = events.filter { it.type == EventType.INCREMENTAL_SNAPSHOT }
        assertEquals(2, incrementalEvents.size, "Incremental capture should contain 2 INCREMENTAL_SNAPSHOT events")
    }
}