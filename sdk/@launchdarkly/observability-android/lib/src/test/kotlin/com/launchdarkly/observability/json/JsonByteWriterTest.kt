package com.launchdarkly.observability.json

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class JsonByteWriterTest {

    private fun write(block: JsonByteWriter.() -> Unit): String = JsonByteWriter.encodeToString(write = block)

    private fun quoted(value: String): String = write { value(value) }

    @Nested
    inner class Structure {

        @Test
        fun `empty containers`() {
            assertEquals("{}", write { beginObject().endObject() })
            assertEquals("[]", write { beginArray().endArray() })
        }

        @Test
        fun `members and elements are separated by commas, names by colons`() {
            val json = write {
                beginObject()
                name("a").value(1)
                name("b").beginArray().value("x").value(true).nullValue().endArray()
                name("c").beginObject().name("d").value(false).endObject()
                name("e").beginArray().endArray()
                endObject()
            }
            assertEquals("""{"a":1,"b":["x",true,null],"c":{"d":false},"e":[]}""", json)
        }

        @Test
        fun `consecutive containers in an array are separated`() {
            val json = write {
                beginArray()
                beginObject().endObject()
                beginObject().name("k").value(1).endObject()
                beginArray().endArray()
                endArray()
            }
            assertEquals("""[{},{"k":1},[]]""", json)
        }

        @Test
        fun `top-level values with no container`() {
            assertEquals("\"s\"", write { value("s") })
            assertEquals("42", write { value(42) })
            assertEquals("null", write { nullValue() })
        }
    }

    @Nested
    inner class Numbers {

        @ParameterizedTest
        @ValueSource(longs = [0, 1, -1, 9, 10, 99, 100, 1234567890, -1234567890, Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1])
        fun `longs are written as Long toString spells them`(value: Long) {
            assertEquals(value.toString(), write { value(value) })
        }

        @Test
        fun `ints widen to the same digits`() {
            assertEquals("-2147483648", write { value(Int.MIN_VALUE) })
            assertEquals("2147483647", write { value(Int.MAX_VALUE) })
        }

        @ParameterizedTest
        @ValueSource(doubles = [0.0, -0.0, 1.0, -1.5, 0.1, 9.99, 1.0E20, 1.0E-7, 123456.789, Double.MAX_VALUE, Double.MIN_VALUE])
        fun `doubles are written as Double toString spells them`(value: Double) {
            assertEquals(value.toString(), write { value(value) })
        }

        @ParameterizedTest
        @ValueSource(doubles = [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY])
        fun `non-finite doubles are refused`(value: Double) {
            assertThrows<IllegalArgumentException> { write { value(value) } }
        }
    }

    @Nested
    inner class Strings {

        @Test
        fun `quotes and backslashes are escaped`() {
            assertEquals(""""a\"b\\c"""", quoted("a\"b\\c"))
        }

        @Test
        fun `control characters use short escapes where JSON has one`() {
            assertEquals(""""\t\b\n\r\f"""", quoted("\t\b\n\r\u000C"))
        }

        @Test
        fun `other control characters use unicode escapes`() {
            assertEquals(""""\u0000\u0001\u001f"""", quoted("\u0000\u0001\u001F"))
        }

        @Test
        fun `DEL and printable ASCII are written as they are`() {
            val printable = (0x20 until 0x80).map { it.toChar() }.filter { it != '"' && it != '\\' }.joinToString("")
            assertEquals("\"$printable\"", quoted(printable))
        }

        @Test
        fun `HTML characters are not escaped`() {
            assertEquals(""""<a href='x'>&=</a>"""", quoted("<a href='x'>&=</a>"))
        }

        @Test
        fun `line and paragraph separators are escaped`() {
            assertEquals(""""a\u2028b\u2029c"""", quoted("a\u2028b\u2029c"))
        }

        @Test
        fun `non-ASCII text is UTF-8 encoded`() {
            val text = "é€\uD83D\uDE00日本"
            val bytes = JsonByteWriter.encode { value(text) }
            assertArrayEquals("\"$text\"".toByteArray(Charsets.UTF_8), bytes)
        }

        @Test
        fun `boundaries of each UTF-8 width`() {
            val text = "\u007F\u0080\u07FF\u0800\uFFFF\uD800\uDC00\uDBFF\uDFFF"
            assertArrayEquals("\"$text\"".toByteArray(Charsets.UTF_8), JsonByteWriter.encode { value(text) })
        }

        @Test
        fun `a surrogate without its other half is written as a question mark`() {
            assertEquals("\"a?b\"", quoted("a\uD800b"))
            assertEquals("\"a?b\"", quoted("a\uDC00b"))
            assertEquals("\"a?\"", quoted("a\uD800"))
            assertEquals("\"??\"", quoted("\uDC00\uD800"))
        }

        @Test
        fun `a null string is written as null`() {
            assertEquals("""{"k":null}""", write { beginObject().name("k").value(null as String?).endObject() })
        }

        @Test
        fun `names are escaped like values`() {
            assertEquals("""{"a\"\n€":1}""", write { beginObject().name("a\"\n€").value(1).endObject() })
        }
    }

    @Nested
    inner class Optional {

        @Test
        fun `null optional members are left out, name and all`() {
            val json = write {
                beginObject()
                optional("s", null as String?)
                optional("b", null as Boolean?)
                optional("i", null as Int?)
                optional("l", null as Long?)
                optional("d", null as Double?)
                name("kept").value(1)
                endObject()
            }
            assertEquals("""{"kept":1}""", json)
        }

        @Test
        fun `present optional members are written`() {
            val json = write {
                beginObject()
                optional("s", "x")
                optional("b", false)
                optional("i", 1)
                optional("l", 2L)
                optional("d", 3.5)
                endObject()
            }
            assertEquals("""{"s":"x","b":false,"i":1,"l":2,"d":3.5}""", json)
        }
    }

    @Nested
    inner class AnyValue {

        @Test
        fun `maps, lists and primitives are written by their runtime type`() {
            val value = linkedMapOf(
                "s" to "x",
                "i" to 1,
                "l" to 2L,
                "d" to 1.5,
                "f" to 2.5f,
                "b" to true,
                "n" to null,
                "list" to listOf(1, "two", null, listOf<Any>()),
                "array" to arrayOf("a"),
                "map" to mapOf(1 to "keyed by toString"),
            )
            assertEquals(
                """{"s":"x","i":1,"l":2,"d":1.5,"f":2.5,"b":true,"n":null,"list":[1,"two",null,[]],"array":["a"],"map":{"1":"keyed by toString"}}""",
                write { anyValue(value) }
            )
        }

        @Test
        fun `floats are written as Float toString spells them`() {
            assertEquals("0.1", write { anyValue(0.1f) })
        }

        @Test
        fun `a JsonWritable writes itself`() {
            val writable = JsonWritable { it.beginObject().name("own").value(true).endObject() }
            assertEquals("""[{"own":true},1]""", write { anyValue(listOf(writable, 1)) })
        }

        @Test
        fun `unsupported types are refused`() {
            assertThrows<IllegalArgumentException> { write { anyValue(Any()) } }
        }

        @Test
        fun `non-finite numbers are refused`() {
            assertThrows<IllegalArgumentException> { write { anyValue(Float.NaN) } }
            assertThrows<IllegalArgumentException> { write { anyValue(Double.POSITIVE_INFINITY) } }
        }
    }

    @Nested
    inner class Buffer {

        @Test
        fun `raw JSON is placed where a value goes`() {
            val raw = JsonByteWriter.encode { beginObject().name("x").value(1).endObject() }
            assertEquals("""[1,{"x":1},2]""", write { beginArray().value(1).writeRaw(raw).value(2).endArray() })
        }

        @Test
        fun `reset starts a new document in the same buffer`() {
            val writer = JsonByteWriter()
            writer.beginArray().value(1).endArray()
            writer.reset()
            writer.value(2)
            assertEquals("2", writer.toUtf8String())
            assertEquals(1, writer.size)
        }

        @Test
        fun `copies are independent of the buffer`() {
            val writer = JsonByteWriter()
            writer.beginArray().value("a")
            val offset = writer.size
            writer.value("b").endArray()
            val copy = writer.toByteArray()
            val tail = writer.bytesFrom(offset)
            writer.reset()
            writer.value("overwritten")
            assertEquals("""["a","b"]""", String(copy, Charsets.UTF_8))
            assertEquals(""","b"]""", String(tail, Charsets.UTF_8))
        }

        @Test
        fun `the buffer grows from a tiny capacity`() {
            val writer = JsonByteWriter(initialCapacity = 1)
            writer.beginArray()
            repeat(1_000) { writer.value(it).value("€$it") }
            writer.endArray()
            val parsed = JSONArray(writer.toUtf8String())
            assertEquals(2_000, parsed.length())
            assertEquals("€999", parsed.getString(1_999))
        }

        @Test
        fun `a long ASCII string does not reserve room for escapes`() {
            val writer = JsonByteWriter(initialCapacity = 16)
            val ascii = "A".repeat(100_000)
            writer.value(ascii)
            assertEquals(100_002, writer.size)
        }

        @Test
        fun `wide characters late in a long string still fit`() {
            val text = "A".repeat(10_000) + "\u0001€\uD83D\uDE00\u2028"
            val writer = JsonByteWriter(initialCapacity = 16)
            writer.value(text)
            assertEquals(text, JSONArray("[${writer.toUtf8String()}]").getString(0))
        }
    }

    @Test
    fun `random strings survive a round trip through a JSON parser`() {
        val random = Random(42)
        val alphabet = listOf("a", "Z", " ", "\"", "\\", "\n", "\u0000", "\u001F", "\u007F", "é", "€", "\u2028", "日", "\uD83D\uDE00")
        repeat(500) {
            val text = buildString {
                repeat(random.nextInt(0, 64)) { append(alphabet[random.nextInt(alphabet.size)]) }
            }
            val json = write { beginObject().name(text).value(text).endObject() }
            val parsed = JSONObject(json)
            assertEquals(text, parsed.getString(text))
        }
    }
}
