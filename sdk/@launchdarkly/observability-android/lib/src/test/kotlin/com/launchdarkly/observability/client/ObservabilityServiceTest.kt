package com.launchdarkly.observability.client

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.sampling.ExportSampler
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.resources.Resource
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObservabilityServiceTest {

    private lateinit var mockSdkLoggerProviderBuilder: SdkLoggerProviderBuilder
    private lateinit var mockExportSampler: ExportSampler
    private lateinit var mockLogger: ObserveLogger
    private lateinit var testResource: Resource
    private lateinit var testSdkKey: String
    private lateinit var testObservabilityOptions: ObservabilityOptions

    @BeforeEach
    fun setup() {
        mockSdkLoggerProviderBuilder = mockk(relaxed = true)
        mockExportSampler = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        testResource = Resource.create(Attributes.empty())
        testSdkKey = "test-sdk-key"
        testObservabilityOptions = ObservabilityOptions()
    }

    @Test
    fun `createLoggerProcessor returns a valid processor`() {
        val logProcessor = ObservabilityService.createLoggerProcessor(
            sdkLoggerProviderBuilder = mockSdkLoggerProviderBuilder,
            exportSampler = mockExportSampler,
            sdkKey = testSdkKey,
            resource = testResource,
            logger = mockLogger,
            telemetryInspector = null,
            observabilityOptions = testObservabilityOptions,
        )

        assertNotNull(logProcessor)
        verify { mockSdkLoggerProviderBuilder.setResource(testResource) }
    }
}
