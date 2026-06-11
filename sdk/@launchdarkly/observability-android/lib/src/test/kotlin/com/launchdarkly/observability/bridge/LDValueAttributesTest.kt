package com.launchdarkly.observability.bridge

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Exercises the `Map<String, Any?>.toOtelAttributes()` mapping used by the `track`
 * paths (`ObservabilityService.track` / `LDObserveBridge.track`).
 *
 * Scalars convert to scalar attributes, homogeneous lists to array attributes,
 * nested maps are flattened with dotted keys, and a whole [Attributes] value is
 * merged in (keys prefixed, value types preserved). Values with no attribute form
 * are dropped rather than stringified.
 */
class LDValueAttributesTest {

    private data class Arbitrary(val name: String)

    @Test
    fun `converts boolean, number and string members`() {
        val data: Map<String, Any?> = mapOf(
            "flag" to true,
            "off" to false,
            "int" to 42,
            "long" to 7L,
            "float" to 1.5f,
            "double" to 3.14,
            "name" to "checkout"
        )

        val attrs = data.toOtelAttributes()

        assertEquals(true, attrs.get(AttributeKey.booleanKey("flag")))
        assertEquals(false, attrs.get(AttributeKey.booleanKey("off")))
        assertEquals(42.0, attrs.get(AttributeKey.doubleKey("int")))
        assertEquals(7.0, attrs.get(AttributeKey.doubleKey("long")))
        assertEquals(1.5, attrs.get(AttributeKey.doubleKey("float")))
        assertEquals(3.14, attrs.get(AttributeKey.doubleKey("double")))
        assertEquals("checkout", attrs.get(AttributeKey.stringKey("name")))
        assertEquals(7, attrs.size())
    }

    @Test
    fun `numbers are normalized to double attributes`() {
        val data: Map<String, Any?> = mapOf("int" to 42)

        val attrs = data.toOtelAttributes()

        assertEquals(42.0, attrs.get(AttributeKey.doubleKey("int")))
        assertNull(attrs.get(AttributeKey.longKey("int")))
        assertNull(attrs.get(AttributeKey.stringKey("int")))
    }

    @Test
    fun `converts homogeneous lists into array attributes`() {
        val data: Map<String, Any?> = mapOf(
            "tags" to listOf("a", "b"),
            "flags" to listOf(true, false),
            "scores" to listOf(1, 2, 3)
        )

        val attrs = data.toOtelAttributes()

        assertEquals(listOf("a", "b"), attrs.get(AttributeKey.stringArrayKey("tags")))
        assertEquals(listOf(true, false), attrs.get(AttributeKey.booleanArrayKey("flags")))
        assertEquals(listOf(1.0, 2.0, 3.0), attrs.get(AttributeKey.doubleArrayKey("scores")))
    }

    @Test
    fun `flattens lists of objects with indexed dotted keys`() {
        val data: Map<String, Any?> = mapOf(
            "products" to listOf(
                mapOf("product_id" to "SKU-1234", "quantity" to 2),
                mapOf("product_id" to "SKU-9876", "quantity" to 1)
            )
        )

        val attrs = data.toOtelAttributes()

        assertEquals("SKU-1234", attrs.get(AttributeKey.stringKey("products.0.product_id")))
        assertEquals(2.0, attrs.get(AttributeKey.doubleKey("products.0.quantity")))
        assertEquals("SKU-9876", attrs.get(AttributeKey.stringKey("products.1.product_id")))
        assertEquals(1.0, attrs.get(AttributeKey.doubleKey("products.1.quantity")))
        assertEquals(4, attrs.size())
    }

    // MARK: - Segment e-commerce examples from analytics-taxonomy.md (§4.2)

    @Test
    fun `Product Added flat payload`() {
        val data: Map<String, Any?> = mapOf(
            "name" to "Product Added",
            "product_id" to "SKU-1234",
            "quantity" to 2,
            "price" to 24.0,
            "currency" to "USD",
            "cart_id" to "cart_98f1"
        )

        val attrs = data.toOtelAttributes()

        assertEquals("Product Added", attrs.get(AttributeKey.stringKey("name")))
        assertEquals("SKU-1234", attrs.get(AttributeKey.stringKey("product_id")))
        assertEquals(2.0, attrs.get(AttributeKey.doubleKey("quantity")))
        assertEquals(24.0, attrs.get(AttributeKey.doubleKey("price")))
        assertEquals("USD", attrs.get(AttributeKey.stringKey("currency")))
        assertEquals("cart_98f1", attrs.get(AttributeKey.stringKey("cart_id")))
        assertEquals(6, attrs.size())
    }

    @Test
    fun `Checkout Started nested products payload`() {
        val data: Map<String, Any?> = mapOf(
            "name" to "Checkout Started",
            "order_id" to "ord_5521",
            "value" to 72.0,
            "currency" to "USD",
            "products" to listOf(
                mapOf("product_id" to "SKU-1234", "quantity" to 2, "price" to 24.0),
                mapOf("product_id" to "SKU-9876", "quantity" to 1, "price" to 24.0)
            )
        )

        val attrs = data.toOtelAttributes()

        assertEquals("Checkout Started", attrs.get(AttributeKey.stringKey("name")))
        assertEquals("ord_5521", attrs.get(AttributeKey.stringKey("order_id")))
        assertEquals(72.0, attrs.get(AttributeKey.doubleKey("value")))
        assertEquals("USD", attrs.get(AttributeKey.stringKey("currency")))
        // The products array of objects is flattened with indexed dotted keys.
        assertEquals("SKU-1234", attrs.get(AttributeKey.stringKey("products.0.product_id")))
        assertEquals(2.0, attrs.get(AttributeKey.doubleKey("products.0.quantity")))
        assertEquals(24.0, attrs.get(AttributeKey.doubleKey("products.0.price")))
        assertEquals("SKU-9876", attrs.get(AttributeKey.stringKey("products.1.product_id")))
        assertEquals(1.0, attrs.get(AttributeKey.doubleKey("products.1.quantity")))
        assertEquals(24.0, attrs.get(AttributeKey.doubleKey("products.1.price")))
    }

    @Test
    fun `flattens nested maps with dotted keys`() {
        val data: Map<String, Any?> = mapOf(
            "user" to mapOf(
                "id" to "u-1",
                "premium" to true,
                "address" to mapOf("city" to "SF")
            )
        )

        val attrs = data.toOtelAttributes()

        assertEquals("u-1", attrs.get(AttributeKey.stringKey("user.id")))
        assertEquals(true, attrs.get(AttributeKey.booleanKey("user.premium")))
        assertEquals("SF", attrs.get(AttributeKey.stringKey("user.address.city")))
        assertEquals(3, attrs.size())
    }

    @Test
    fun `merges a whole Attributes value preserving key types`() {
        val inner = Attributes.builder()
            .put(AttributeKey.stringKey("name"), "y")
            .put(AttributeKey.longKey("n"), 5L)
            .put(AttributeKey.stringArrayKey("tags"), listOf("a", "b"))
            .build()
        val data: Map<String, Any?> = mapOf(
            "event" to "signup",
            "payload" to inner
        )

        val attrs = data.toOtelAttributes()

        assertEquals("signup", attrs.get(AttributeKey.stringKey("event")))
        assertEquals("y", attrs.get(AttributeKey.stringKey("payload.name")))
        // The long stays a long (not coerced to double or stringified).
        assertEquals(5L, attrs.get(AttributeKey.longKey("payload.n")))
        assertEquals(listOf("a", "b"), attrs.get(AttributeKey.stringArrayKey("payload.tags")))
        assertEquals(4, attrs.size())
    }

    @Test
    fun `skips null values`() {
        val data: Map<String, Any?> = mapOf(
            "keep" to "yes",
            "missing" to null
        )

        val attrs = data.toOtelAttributes()

        assertEquals("yes", attrs.get(AttributeKey.stringKey("keep")))
        assertNull(attrs.get(AttributeKey.stringKey("missing")))
        assertEquals(1, attrs.size())
    }

    @Test
    fun `skips arbitrary objects without stringifying`() {
        val data: Map<String, Any?> = mapOf(
            "keep" to 1,
            "object" to Arbitrary("payload")
        )

        val attrs = data.toOtelAttributes()

        assertEquals(1.0, attrs.get(AttributeKey.doubleKey("keep")))
        assertNull(attrs.get(AttributeKey.stringKey("object")))
        assertEquals(1, attrs.size())
    }

    @Test
    fun `empty map yields empty attributes`() {
        assertEquals(0, emptyMap<String, Any?>().toOtelAttributes().size())
    }
}
