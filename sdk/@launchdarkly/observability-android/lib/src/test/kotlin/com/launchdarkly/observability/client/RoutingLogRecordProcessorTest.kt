package com.launchdarkly.observability.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RoutingLogRecordProcessorTest {

    private lateinit var mockLogRecordProcessor: LogRecordProcessor
    private lateinit var mockFallthroughProcessor: LogRecordProcessor
    private lateinit var routingProcessor: RoutingLogRecordProcessor

    @BeforeEach
    fun setup() {
        mockLogRecordProcessor = mockk(relaxed = true)
        mockFallthroughProcessor = mockk(relaxed = true)
        routingProcessor = RoutingLogRecordProcessor(fallthroughProcessor = mockFallthroughProcessor)
    }

    @Test
    fun `should route logs to registered processor when scope name matches`() {
        // Arrange
        val testScopeName = "com.test.instrumentation"
        val mockContext = Context.current()
        
        // Create mock log record with matching scope
        val mockLogRecord = mockk<ReadWriteLogRecord>(relaxed = true)
        val mockInstrumentationScopeInfo = mockk<InstrumentationScopeInfo>(relaxed = true)
        
        every { mockLogRecord.instrumentationScopeInfo } returns mockInstrumentationScopeInfo
        every { mockInstrumentationScopeInfo.name } returns testScopeName

        // Register the processor for the test scope
        routingProcessor.registerProcessor(testScopeName, mockLogRecordProcessor)

        // Act
        routingProcessor.onEmit(mockContext, mockLogRecord)

        // Assert - Should route to the registered processor
        verify { mockLogRecordProcessor.onEmit(mockContext, mockLogRecord) }
        verify(exactly = 0) { mockFallthroughProcessor.onEmit(any(), any()) }
    }

    @Test
    fun `should route logs to fallthrough processor when scope name does not match`() {
        // Arrange
        val testScopeName = "com.test.instrumentation"
        val nonMatchingScopeName = "com.random.scope"
        val mockContext = Context.current()
        
        // Create mock log record with non-matching scope
        val mockLogRecord = mockk<ReadWriteLogRecord>(relaxed = true)
        val mockInstrumentationScopeInfo = mockk<InstrumentationScopeInfo>(relaxed = true)
        
        every { mockLogRecord.instrumentationScopeInfo } returns mockInstrumentationScopeInfo
        every { mockInstrumentationScopeInfo.name } returns nonMatchingScopeName

        // Register the processor for a different scope
        routingProcessor.registerProcessor(testScopeName, mockLogRecordProcessor)

        // Act
        routingProcessor.onEmit(mockContext, mockLogRecord)

        // Assert - Should route to fallthrough processor since scope doesn't match
        verify { mockFallthroughProcessor.onEmit(mockContext, mockLogRecord) }
        verify(exactly = 0) { mockLogRecordProcessor.onEmit(any(), any()) }
    }

    @Test
    fun `should handle multiple registered processors correctly`() {
        // Arrange
        val scopeName1 = "com.test.instrumentation1"
        val scopeName2 = "com.test.instrumentation2"
        val mockContext = Context.current()
        
        val mockProcessor1 = mockk<LogRecordProcessor>(relaxed = true)
        val mockProcessor2 = mockk<LogRecordProcessor>(relaxed = true)
        
        // Create mock log records for each scope
        val mockLogRecord1 = mockk<ReadWriteLogRecord>(relaxed = true)
        val mockLogRecord2 = mockk<ReadWriteLogRecord>(relaxed = true)
        
        val mockScopeInfo1 = mockk<InstrumentationScopeInfo>(relaxed = true)
        val mockScopeInfo2 = mockk<InstrumentationScopeInfo>(relaxed = true)
        
        every { mockLogRecord1.instrumentationScopeInfo } returns mockScopeInfo1
        every { mockLogRecord2.instrumentationScopeInfo } returns mockScopeInfo2
        every { mockScopeInfo1.name } returns scopeName1
        every { mockScopeInfo2.name } returns scopeName2

        // Register both processors
        routingProcessor.registerProcessor(scopeName1, mockProcessor1)
        routingProcessor.registerProcessor(scopeName2, mockProcessor2)

        // Act & Assert - Test first processor
        routingProcessor.onEmit(mockContext, mockLogRecord1)
        verify { mockProcessor1.onEmit(mockContext, mockLogRecord1) }

        // Act & Assert - Test second processor
        routingProcessor.onEmit(mockContext, mockLogRecord2)
        verify { mockProcessor2.onEmit(mockContext, mockLogRecord2) }
    }

    @Test
    fun `should use fallthrough processor when no processors are registered`() {
        // Arrange
        val testScopeName = "com.test.instrumentation"
        val mockContext = Context.current()
        
        // Create mock log record
        val mockLogRecord = mockk<ReadWriteLogRecord>(relaxed = true)
        val mockInstrumentationScopeInfo = mockk<InstrumentationScopeInfo>(relaxed = true)
        
        every { mockLogRecord.instrumentationScopeInfo } returns mockInstrumentationScopeInfo
        every { mockInstrumentationScopeInfo.name } returns testScopeName

        // Don't register any processors

        // Act
        routingProcessor.onEmit(mockContext, mockLogRecord)

        // Assert - Should use fallthrough processor since no processors are registered
        verify { mockFallthroughProcessor.onEmit(mockContext, mockLogRecord) }
    }

    @Test
    fun `should handle empty scope name correctly`() {
        // Arrange
        val testScopeName = "com.test.instrumentation"
        val emptyScopeName = ""
        val mockContext = Context.current()
        
        // Create mock log record with empty scope name
        val mockLogRecord = mockk<ReadWriteLogRecord>(relaxed = true)
        val mockInstrumentationScopeInfo = mockk<InstrumentationScopeInfo>(relaxed = true)
        
        every { mockLogRecord.instrumentationScopeInfo } returns mockInstrumentationScopeInfo
        every { mockInstrumentationScopeInfo.name } returns emptyScopeName

        // Register processor for a different scope
        routingProcessor.registerProcessor(testScopeName, mockLogRecordProcessor)

        // Act
        routingProcessor.onEmit(mockContext, mockLogRecord)

        // Assert - Should use fallthrough processor since scope names don't match
        verify { mockFallthroughProcessor.onEmit(mockContext, mockLogRecord) }
        verify(exactly = 0) { mockLogRecordProcessor.onEmit(any(), any()) }
    }
}
