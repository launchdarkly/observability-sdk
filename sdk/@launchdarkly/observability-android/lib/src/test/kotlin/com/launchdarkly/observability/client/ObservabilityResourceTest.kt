package com.launchdarkly.observability.client

import com.launchdarkly.observability.api.ObservabilityOptions
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [buildObservabilityResource], covering the `service.name` / `service.version`
 * resource attributes that backends use to identify the emitting service.
 */
class ObservabilityResourceTest {
    private val serviceName = AttributeKey.stringKey("service.name")
    private val serviceVersion = AttributeKey.stringKey("service.version")

    @Test
    fun `service name and version from options are written to the resource`() {
        val resource = buildObservabilityResource(
            sdkKey = "test-key",
            options = ObservabilityOptions(
                serviceName = "my-service",
                serviceVersion = "1.2.3",
            ),
        )

        assertEquals("my-service", resource.attributes.get(serviceName))
        assertEquals("1.2.3", resource.attributes.get(serviceVersion))
    }

    @Test
    fun `options service name overrides one supplied via resourceAttributes`() {
        val resource = buildObservabilityResource(
            sdkKey = "test-key",
            options = ObservabilityOptions(
                serviceName = "configured-service",
                resourceAttributes = Attributes.of(serviceName, "attr-service"),
            ),
        )

        assertEquals("configured-service", resource.attributes.get(serviceName))
    }
}
