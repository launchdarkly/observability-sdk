package com.launchdarkly.observability.client

import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.Options
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.observability.sampling.ExportSampler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.resources.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Test class focused on testing the createLoggerProcessor method logic.
 * This test verifies that the RoutingLogRecordProcessor is properly configured
 * with instrumentation-specific log record processors.
 */
class InstrumentationManagerTest {

    private lateinit var mockSdkLoggerProviderBuilder: SdkLoggerProviderBuilder
    private lateinit var mockExportSampler: ExportSampler
    private lateinit var mockLogger: LDLogger
    private lateinit var testResource: Resource
    private lateinit var testSdkKey: String
    private lateinit var testOptions: Options

    @BeforeEach
    fun setup() {
        mockSdkLoggerProviderBuilder = mockk(relaxed = true)
        mockExportSampler = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        testResource = Resource.create(Attributes.empty())
        testSdkKey = "test-sdk-key"
        testOptions = Options()
    }

    @Test
    fun `createLoggerProcessor should register instrumentation log record processors with correct scope names`() {
        // Arrange
        val mockInstrumentation1 = mockk<LDExtendedInstrumentation>(relaxed = true)
        val mockInstrumentation2 = mockk<LDExtendedInstrumentation>(relaxed = true)
        val mockLogRecordProcessor1 = mockk<io.opentelemetry.sdk.logs.LogRecordProcessor>(relaxed = true)
        val mockLogRecordProcessor2 = mockk<io.opentelemetry.sdk.logs.LogRecordProcessor>(relaxed = true)
        
        val scopeName1 = "com.test.instrumentation1"
        val scopeName2 = "com.test.instrumentation2"
        
        every { mockInstrumentation1.getLoggerScopeName() } returns scopeName1
        every { mockInstrumentation1.getLogRecordProcessor(testSdkKey) } returns mockLogRecordProcessor1
        every { mockInstrumentation2.getLoggerScopeName() } returns scopeName2
        every { mockInstrumentation2.getLogRecordProcessor(testSdkKey) } returns mockLogRecordProcessor2

        testOptions = Options(instrumentations = listOf(mockInstrumentation1, mockInstrumentation2))

        // Act
        val logProcessor = InstrumentationManager.createLoggerProcessor(
            mockSdkLoggerProviderBuilder,
            mockExportSampler,
            testSdkKey,
            testResource,
            mockLogger,
            null,
            testOptions
        )

        // Assert
        assertNotNull(logProcessor)
        
        // Verify that the logger provider builder was configured with resource
        verify { mockSdkLoggerProviderBuilder.setResource(testResource) }
        
        // Verify that instrumentation methods were called
        verify { mockInstrumentation1.getLoggerScopeName() }
        verify { mockInstrumentation1.getLogRecordProcessor(testSdkKey) }
        verify { mockInstrumentation2.getLoggerScopeName() }
        verify { mockInstrumentation2.getLogRecordProcessor(testSdkKey) }
    }

    @Test
    fun `createLoggerProcessor should handle instrumentations with null log record processors`() {
        // Arrange
        val mockInstrumentation = mockk<LDExtendedInstrumentation>(relaxed = true)
        val scopeName = "com.test.instrumentation"
        
        every { mockInstrumentation.getLoggerScopeName() } returns scopeName
        every { mockInstrumentation.getLogRecordProcessor(testSdkKey) } returns null

        testOptions = Options(instrumentations = listOf(mockInstrumentation))

        // Act
        val logProcessor = InstrumentationManager.createLoggerProcessor(
            mockSdkLoggerProviderBuilder,
            mockExportSampler,
            testSdkKey,
            testResource,
            mockLogger,
            null,
            testOptions
        )

        // Assert
        assertNotNull(logProcessor)
        
        // Verify that the logger provider builder was configured
        verify { mockSdkLoggerProviderBuilder.setResource(testResource) }
        
        // Verify that instrumentation methods were called
        verify { mockInstrumentation.getLogRecordProcessor(testSdkKey) }
        // Verify that getLoggerScopeName() is NOT called when getLogRecordProcessor returns null
        verify(exactly = 0) { mockInstrumentation.getLoggerScopeName() }
    }

    @Test
    fun `createLoggerProcessor should handle empty instrumentations list`() {
        // Arrange
        testOptions = Options(instrumentations = emptyList())

        // Act
        val logProcessor = InstrumentationManager.createLoggerProcessor(
            mockSdkLoggerProviderBuilder,
            mockExportSampler,
            testSdkKey,
            testResource,
            mockLogger,
            null,
            testOptions
        )

        // Assert
        assertNotNull(logProcessor)
        
        // Verify that the logger provider builder was configured
        verify { mockSdkLoggerProviderBuilder.setResource(testResource) }
    }
}