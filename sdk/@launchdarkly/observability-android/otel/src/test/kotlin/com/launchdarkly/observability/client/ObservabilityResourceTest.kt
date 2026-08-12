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
    private val symbolsIdKey = AttributeKey.stringKey("launchdarkly.symbols_id.htlhash")

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

    @Test
    fun `symbols id is written to the resource when provided (Symbols Id Lane)`() {
        val resource = buildObservabilityResource(
            sdkKey = "test-key",
            options = ObservabilityOptions(serviceName = "my-service"),
            symbolsId = "0123456789abcdef0123456789abcdef",
        )

        assertEquals(
            "0123456789abcdef0123456789abcdef",
            resource.attributes.get(symbolsIdKey),
        )
    }

    @Test
    fun `symbols id attribute is omitted when not provided`() {
        val resource = buildObservabilityResource(
            sdkKey = "test-key",
            options = ObservabilityOptions(serviceName = "my-service"),
        )

        assertEquals(null, resource.attributes.get(symbolsIdKey))
    }
}
