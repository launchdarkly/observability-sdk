package com.launchdarkly.observability.json

/**
 * Appends compact JSON, already UTF-8 encoded, to a byte array. Callers are responsible for well-formed
 * nesting: every [name] is followed by exactly one value, and every container that is begun is ended.
 *
 * A string is escaped and encoded in one pass straight into the array that is sent, with no UTF-16
 * buffer, charset encoder or intermediate copies between a payload and its bytes.
 *
 * Not thread-safe. One writer serves one payload on one thread.
 */
internal class JsonByteWriter(initialCapacity: Int = DEFAULT_CAPACITY) {
    private var bytes = ByteArray(maxOf(initialCapacity, MIN_CAPACITY))

    var size: Int = 0
        private set

    /**
     * Whether the next value written needs a comma in front of it.
     *
     * Writing a name clears it, so the value that follows the colon is not preceded by one; writing a
     * value or closing a container sets it, so whatever comes next is.
     */
    private var needsSeparator = false

    fun reset() {
        size = 0
        needsSeparator = false
    }

    /** A copy of everything written since the last [reset], independent of this writer's buffer. */
    fun toByteArray(): ByteArray = bytes.copyOf(size)

    /** A copy of the bytes written since [offset], independent of this writer's buffer. */
    fun bytesFrom(offset: Int): ByteArray = bytes.copyOfRange(offset, size)

    /** Everything written since the last [reset], decoded back into a string. */
    fun toUtf8String(): String = String(bytes, 0, size, Charsets.UTF_8)

    /** Appends JSON that was produced earlier, in the position a value would go. */
    fun writeRaw(raw: ByteArray): JsonByteWriter {
        separate()
        ensure(raw.size)
        raw.copyInto(bytes, size)
        size += raw.size
        needsSeparator = true
        return this
    }

    // Structure

    fun beginObject(): JsonByteWriter {
        separate()
        append('{')
        needsSeparator = false
        return this
    }

    fun endObject(): JsonByteWriter {
        append('}')
        needsSeparator = true
        return this
    }

    fun beginArray(): JsonByteWriter {
        separate()
        append('[')
        needsSeparator = false
        return this
    }

    fun endArray(): JsonByteWriter {
        append(']')
        needsSeparator = true
        return this
    }

    fun name(name: String): JsonByteWriter {
        separate()
        writeQuoted(name)
        append(':')
        needsSeparator = false
        return this
    }

    // Values

    fun value(value: String?): JsonByteWriter {
        if (value == null) {
            return nullValue()
        }
        separate()
        writeQuoted(value)
        needsSeparator = true
        return this
    }

    fun value(value: Boolean): JsonByteWriter {
        separate()
        append(if (value) TRUE else FALSE)
        needsSeparator = true
        return this
    }

    fun value(value: Int): JsonByteWriter = value(value.toLong())

    fun value(value: Long): JsonByteWriter {
        separate()
        writeLong(value)
        needsSeparator = true
        return this
    }

    /** Written as [Double.toString] spells it. JSON has no spelling for a non-finite number, so one is refused. */
    fun value(value: Double): JsonByteWriter {
        require(value.isFinite()) { "Numeric values must be finite, but was $value" }
        separate()
        appendAscii(value.toString())
        needsSeparator = true
        return this
    }

    fun nullValue(): JsonByteWriter {
        separate()
        append(NULL)
        needsSeparator = true
        return this
    }

    /**
     * Writes a value whose type is only known at runtime: `null`, [String], [Boolean], any [Number], a
     * [Map] (keys by their `toString`), an [Iterable] or [Array], or a [JsonWritable].
     */
    fun anyValue(value: Any?): JsonByteWriter {
        when (value) {
            null -> nullValue()
            is String -> value(value)
            is Boolean -> value(value)
            is Int -> value(value.toLong())
            is Long -> value(value)
            is Short -> value(value.toLong())
            is Byte -> value(value.toLong())
            is Double -> value(value)
            is Float -> {
                require(value.isFinite()) { "Numeric values must be finite, but was $value" }
                separate()
                appendAscii(value.toString())
                needsSeparator = true
            }
            is Number -> value(value.toDouble())
            is JsonWritable -> value.writeJson(this)
            is Map<*, *> -> {
                beginObject()
                for ((key, member) in value) {
                    name(key.toString())
                    anyValue(member)
                }
                endObject()
            }
            is Iterable<*> -> {
                beginArray()
                for (element in value) {
                    anyValue(element)
                }
                endArray()
            }
            is Array<*> -> {
                beginArray()
                for (element in value) {
                    anyValue(element)
                }
                endArray()
            }
            else -> throw IllegalArgumentException("Cannot write a ${value::class.java.name} as JSON")
        }
        return this
    }

    // Optional members, left out entirely - name and all - when the value is null

    fun optional(name: String, value: String?): JsonByteWriter =
        if (value == null) this else name(name).value(value)

    fun optional(name: String, value: Boolean?): JsonByteWriter =
        if (value == null) this else name(name).value(value)

    fun optional(name: String, value: Int?): JsonByteWriter =
        if (value == null) this else name(name).value(value)

    fun optional(name: String, value: Long?): JsonByteWriter =
        if (value == null) this else name(name).value(value)

    fun optional(name: String, value: Double?): JsonByteWriter =
        if (value == null) this else name(name).value(value)

    // Primitives

    private fun separate() {
        if (needsSeparator) {
            append(',')
        }
    }

    private fun append(ascii: Char) {
        ensure(1)
        bytes[size++] = ascii.code.toByte()
    }

    private fun append(literal: ByteArray) {
        ensure(literal.size)
        literal.copyInto(bytes, size)
        size += literal.size
    }

    private fun appendAscii(ascii: String) {
        val length = ascii.length
        ensure(length)
        for (i in 0 until length) {
            bytes[size++] = ascii[i].code.toByte()
        }
    }

    /** Digits are written backwards and then flipped in place, so no scratch buffer is allocated per number. */
    private fun writeLong(value: Long) {
        if (value == Long.MIN_VALUE) {
            append(LONG_MIN_VALUE)
            return
        }
        ensure(MAX_LONG_LENGTH)
        var remaining = value
        if (remaining < 0) {
            bytes[size++] = '-'.code.toByte()
            remaining = -remaining
        }
        if (remaining == 0L) {
            bytes[size++] = '0'.code.toByte()
            return
        }
        val start = size
        while (remaining > 0) {
            bytes[size++] = ('0'.code + (remaining % 10).toInt()).toByte()
            remaining /= 10
        }
        var lower = start
        var upper = size - 1
        while (lower < upper) {
            val swap = bytes[lower]
            bytes[lower] = bytes[upper]
            bytes[upper] = swap
            lower++
            upper--
        }
    }

    /**
     * Escapes every control character, `"` and `\`, plus U+2028 and U+2029, which JavaScript parsers
     * older than ES2019 reject inside a string literal. Encodes the rest as UTF-8, writing a surrogate
     * without its other half as `?`, the replacement [String.toByteArray] would use.
     */
    private fun writeQuoted(value: String) {
        val length = value.length
        // Room for the quotes and one byte per char is reserved up front, so the all-ASCII common case
        // (base64 images included) never over-allocates. Anything wider reserves its extra bytes when
        // it is reached, keeping the invariant that the rest of the string still fits at one byte each.
        ensure(length + 2)
        var b = bytes
        var n = size
        b[n++] = QUOTE
        var i = 0
        while (i < length) {
            val c = value[i].code
            if (c >= 0x20 && c < 0x80 && c != '"'.code && c != '\\'.code) {
                b[n++] = c.toByte()
                i++
                continue
            }
            val remainingAfter = length - i - 1
            if (n + MAX_CHAR_BYTES + remainingAfter + 1 > b.size) {
                size = n
                ensure(MAX_CHAR_BYTES + remainingAfter + 1)
                b = bytes
            }
            if (c < 0x80) {
                n = writeEscape(b, n, c)
            } else if (c < 0x800) {
                b[n++] = (0xC0 or (c shr 6)).toByte()
                b[n++] = (0x80 or (c and 0x3F)).toByte()
            } else if (c in SURROGATE_RANGE) {
                if (c <= MAX_HIGH_SURROGATE && i + 1 < length && value[i + 1].isLowSurrogate()) {
                    val codePoint = Character.toCodePoint(value[i], value[i + 1])
                    i++
                    b[n++] = (0xF0 or (codePoint shr 18)).toByte()
                    b[n++] = (0x80 or ((codePoint shr 12) and 0x3F)).toByte()
                    b[n++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                    b[n++] = (0x80 or (codePoint and 0x3F)).toByte()
                } else {
                    b[n++] = '?'.code.toByte()
                }
            } else if (c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR) {
                n = writeUnicodeEscape(b, n, c)
            } else {
                b[n++] = (0xE0 or (c shr 12)).toByte()
                b[n++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
                b[n++] = (0x80 or (c and 0x3F)).toByte()
            }
            i++
        }
        b[n++] = QUOTE
        size = n
    }

    private fun ensure(additional: Int) {
        val required = size + additional
        if (required > bytes.size) {
            bytes = bytes.copyOf(maxOf(required, bytes.size * 2))
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 1024
        private const val MIN_CAPACITY = 16

        /** The most bytes any one char can take: an escape such as `\u001f`. */
        private const val MAX_CHAR_BYTES = 6
        private const val MAX_LONG_LENGTH = 20
        private const val MAX_HIGH_SURROGATE = 0xDBFF
        private const val LINE_SEPARATOR = 0x2028
        private const val PARAGRAPH_SEPARATOR = 0x2029
        private val SURROGATE_RANGE = 0xD800..0xDFFF

        private const val QUOTE = '"'.code.toByte()
        private val TRUE = "true".toByteArray(Charsets.US_ASCII)
        private val FALSE = "false".toByteArray(Charsets.US_ASCII)
        private val NULL = "null".toByteArray(Charsets.US_ASCII)
        private val LONG_MIN_VALUE = Long.MIN_VALUE.toString().toByteArray(Charsets.US_ASCII)
        private val HEX_DIGITS = "0123456789abcdef".toByteArray(Charsets.US_ASCII)

        /** Writes one JSON document with a fresh writer and returns its bytes. */
        inline fun encode(initialCapacity: Int = DEFAULT_CAPACITY, write: JsonByteWriter.() -> Unit): ByteArray =
            JsonByteWriter(initialCapacity).apply(write).toByteArray()

        /** Writes one JSON document with a fresh writer and returns it as a string. */
        inline fun encodeToString(initialCapacity: Int = DEFAULT_CAPACITY, write: JsonByteWriter.() -> Unit): String =
            JsonByteWriter(initialCapacity).apply(write).toUtf8String()

        private fun writeEscape(b: ByteArray, n: Int, c: Int): Int {
            val escaped = when (c) {
                '"'.code -> '"'
                '\\'.code -> '\\'
                '\t'.code -> 't'
                '\b'.code -> 'b'
                '\n'.code -> 'n'
                '\r'.code -> 'r'
                0x0C -> 'f'
                else -> return writeUnicodeEscape(b, n, c)
            }
            b[n] = '\\'.code.toByte()
            b[n + 1] = escaped.code.toByte()
            return n + 2
        }

        private fun writeUnicodeEscape(b: ByteArray, n: Int, c: Int): Int {
            b[n] = '\\'.code.toByte()
            b[n + 1] = 'u'.code.toByte()
            b[n + 2] = HEX_DIGITS[(c shr 12) and 0xF]
            b[n + 3] = HEX_DIGITS[(c shr 8) and 0xF]
            b[n + 4] = HEX_DIGITS[(c shr 4) and 0xF]
            b[n + 5] = HEX_DIGITS[c and 0xF]
            return n + 6
        }
    }
}

/** Something that writes itself as one JSON value, for use inside [JsonByteWriter.anyValue]. */
internal fun interface JsonWritable {
    fun writeJson(writer: JsonByteWriter)
}
