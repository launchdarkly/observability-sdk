package com.launchdarkly.observability.replay

import com.launchdarkly.observability.json.JsonByteWriter
import com.launchdarkly.observability.json.JsonWritable

/**
 * A `data:` URL whose base64 is kept as the ASCII bytes the encoder produced. It is written to JSON by
 * copying those bytes, so a screenshot is never held as a UTF-16 string on its way into the payload. As a
 * [CharSequence] it reads as the URL's text.
 */
internal class Base64DataUrl(mimeType: String, private val base64: ByteArray) : CharSequence, JsonWritable {
    private val prefix = "data:$mimeType;base64,".toByteArray(Charsets.US_ASCII)

    override val length: Int
        get() = prefix.size + base64.size

    override fun get(index: Int): Char =
        (if (index < prefix.size) prefix[index] else base64[index - prefix.size]).toInt().toChar()

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        toString().substring(startIndex, endIndex)

    override fun toString(): String = String(prefix + base64, Charsets.US_ASCII)

    override fun writeJson(writer: JsonByteWriter) {
        writer.asciiValue(prefix, base64)
    }

    override fun equals(other: Any?): Boolean =
        other is Base64DataUrl && prefix.contentEquals(other.prefix) && base64.contentEquals(other.base64)

    override fun hashCode(): Int = 31 * prefix.contentHashCode() + base64.contentHashCode()
}
