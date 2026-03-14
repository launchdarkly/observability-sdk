package com.launchdarkly.observability.sdk

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AttributeConverterTest {

    @Nested
    inner class ScalarValues {

        @Test
        fun `converts String value`() {
            val result = AttributeConverter.convert(mapOf("key" to "hello"))
            assertEquals("hello", result.get(AttributeKey.stringKey("key")))
        }

        @Test
        fun `converts Boolean value`() {
            val result = AttributeConverter.convert(mapOf("flag" to true))
            assertEquals(true, result.get(AttributeKey.booleanKey("flag")))
        }

        @Test
        fun `converts Int value as Long`() {
            val result = AttributeConverter.convert(mapOf("count" to 42))
            assertEquals(42L, result.get(AttributeKey.longKey("count")))
        }

        @Test
        fun `converts Long value`() {
            val result = AttributeConverter.convert(mapOf("big" to 123456789L))
            assertEquals(123456789L, result.get(AttributeKey.longKey("big")))
        }

        @Test
        fun `converts Double value`() {
            val result = AttributeConverter.convert(mapOf("rate" to 3.14))
            assertEquals(3.14, result.get(AttributeKey.doubleKey("rate")))
        }

        @Test
        fun `converts Float value as Double`() {
            val result = AttributeConverter.convert(mapOf("rate" to 1.5f))
            assertEquals(1.5, result.get(AttributeKey.doubleKey("rate")))
        }

        @Test
        fun `falls back to string for unsupported types`() {
            val obj = object {
                override fun toString() = "custom-object"
            }
            val result = AttributeConverter.convert(mapOf("thing" to obj))
            assertEquals("custom-object", result.get(AttributeKey.stringKey("thing")))
        }

        @Test
        fun `skips null values`() {
            val result = AttributeConverter.convert(mapOf("key" to null))
            assertTrue(result.isEmpty)
        }
    }

    @Nested
    inner class NestedMaps {

        @Test
        fun `flattens nested map with dot-separated keys`() {
            val source = mapOf(
                "parent" to mapOf("child" to "value")
            )
            val result = AttributeConverter.convert(source)
            assertEquals("value", result.get(AttributeKey.stringKey("parent.child")))
        }

        @Test
        fun `flattens deeply nested maps`() {
            val source = mapOf(
                "level1" to mapOf(
                    "level2" to mapOf(
                        "level3" to mapOf(
                            "value" to 42
                        )
                    )
                )
            )
            val result = AttributeConverter.convert(source)
            assertEquals(42L, result.get(AttributeKey.longKey("level1.level2.level3.value")))
        }

        @Test
        fun `flattens nested map alongside flat keys`() {
            val source = mapOf(
                "flat" to "top",
                "nested" to mapOf("inner" to "deep")
            )
            val result = AttributeConverter.convert(source)
            assertEquals("top", result.get(AttributeKey.stringKey("flat")))
            assertEquals("deep", result.get(AttributeKey.stringKey("nested.inner")))
        }
    }

    @Nested
    inner class ListValues {

        @Test
        fun `converts list of Strings`() {
            val result = AttributeConverter.convert(mapOf("tags" to listOf("a", "b", "c")))
            assertEquals(listOf("a", "b", "c"), result.get(AttributeKey.stringArrayKey("tags")))
        }

        @Test
        fun `converts list of Booleans`() {
            val result = AttributeConverter.convert(mapOf("flags" to listOf(true, false, true)))
            assertEquals(listOf(true, false, true), result.get(AttributeKey.booleanArrayKey("flags")))
        }

        @Test
        fun `converts list of Ints as Longs`() {
            val result = AttributeConverter.convert(mapOf("ids" to listOf(1, 2, 3)))
            assertEquals(listOf(1L, 2L, 3L), result.get(AttributeKey.longArrayKey("ids")))
        }

        @Test
        fun `converts list of Longs`() {
            val result = AttributeConverter.convert(mapOf("ids" to listOf(10L, 20L)))
            assertEquals(listOf(10L, 20L), result.get(AttributeKey.longArrayKey("ids")))
        }

        @Test
        fun `converts list of Doubles`() {
            val result = AttributeConverter.convert(mapOf("rates" to listOf(1.1, 2.2)))
            assertEquals(listOf(1.1, 2.2), result.get(AttributeKey.doubleArrayKey("rates")))
        }

        @Test
        fun `converts list of Floats as Doubles`() {
            val result = AttributeConverter.convert(mapOf("rates" to listOf(1.5f, 2.5f)))
            assertEquals(listOf(1.5, 2.5), result.get(AttributeKey.doubleArrayKey("rates")))
        }

        @Test
        fun `skips empty list`() {
            val result = AttributeConverter.convert(mapOf("empty" to emptyList<Any>()))
            assertNull(result.get(AttributeKey.stringArrayKey("empty")))
        }

        @Test
        fun `skips list of all nulls`() {
            val result = AttributeConverter.convert(mapOf("nulls" to listOf(null, null)))
            assertNull(result.get(AttributeKey.stringArrayKey("nulls")))
        }

        @Test
        fun `falls back to string list for unsupported element types`() {
            data class Custom(val v: Int)
            val result = AttributeConverter.convert(mapOf("objs" to listOf(Custom(1), Custom(2))))
            assertEquals(listOf("Custom(v=1)", "Custom(v=2)"), result.get(AttributeKey.stringArrayKey("objs")))
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `returns empty Attributes for null source`() {
            val result = AttributeConverter.convert(null)
            assertTrue(result.isEmpty)
        }

        @Test
        fun `returns empty Attributes for empty map`() {
            val result = AttributeConverter.convert(emptyMap())
            assertTrue(result.isEmpty)
        }

        @Test
        fun `handles HashMap input`() {
            val source = hashMapOf<String, Any?>("key" to "value")
            val result = AttributeConverter.convert(source)
            assertEquals("value", result.get(AttributeKey.stringKey("key")))
        }
    }

    @Nested
    inner class MauiSamplePayload {

        @Test
        fun `converts the MAUI sample nested payload end-to-end`() {
            val source = mapOf(
                "test-log" to "maui",
                "nested" to mapOf(
                    "array" to listOf(1)
                )
            )

            val result = AttributeConverter.convert(source)

            assertEquals("maui", result.get(AttributeKey.stringKey("test-log")))
            assertEquals(listOf(1L), result.get(AttributeKey.longArrayKey("nested.array")))
        }

        @Test
        fun `converts mixed nested payload`() {
            val source = mapOf(
                "service" to "android",
                "metadata" to mapOf(
                    "version" to "1.0",
                    "flags" to listOf(true, false),
                    "deep" to mapOf(
                        "value" to 99.9
                    )
                )
            )

            val result = AttributeConverter.convert(source)

            assertEquals("android", result.get(AttributeKey.stringKey("service")))
            assertEquals("1.0", result.get(AttributeKey.stringKey("metadata.version")))
            assertEquals(listOf(true, false), result.get(AttributeKey.booleanArrayKey("metadata.flags")))
            assertEquals(99.9, result.get(AttributeKey.doubleKey("metadata.deep.value")))
        }
    }
}
