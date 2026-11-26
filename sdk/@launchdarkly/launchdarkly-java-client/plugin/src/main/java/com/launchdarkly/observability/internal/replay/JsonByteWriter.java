package com.launchdarkly.observability.internal.replay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Appends compact JSON, already UTF-8 encoded, to a growable byte array. Replay payloads carry
 * megabytes of base64 image data; here each string is escaped and encoded in one pass straight into
 * the array that is sent, and image data, already ASCII, is copied in without becoming a string at
 * all. Ported from the Android SDK's {@code JsonByteWriter}, without its Gson compatibility modes.
 * <p>
 * The caller is trusted to nest calls correctly: every {@link #name} is followed by exactly one value,
 * and every container that is begun is ended. Not thread-safe.
 */
final class JsonByteWriter {
  private static final byte[] TRUE = {'t', 'r', 'u', 'e'};
  private static final byte[] FALSE = {'f', 'a', 'l', 's', 'e'};
  private static final byte[] NULL = {'n', 'u', 'l', 'l'};
  private static final byte[] LONG_MIN_VALUE = Long.toString(Long.MIN_VALUE).getBytes(StandardCharsets.US_ASCII);
  private static final byte[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };
  /** The largest array the JVM reliably allocates. */
  private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

  private byte[] bytes;
  private int size;
  /**
   * Whether the next value needs a comma in front of it. Writing a name clears it, so the value after
   * the colon has none; writing a value or closing a container sets it.
   */
  private boolean needsSeparator;

  JsonByteWriter() {
    this(256);
  }

  /** A writer that holds {@code capacity} bytes before it grows; capped at what an array can hold. */
  JsonByteWriter(long capacity) {
    bytes = new byte[(int) Math.max(16, Math.min(MAX_CAPACITY, capacity))];
  }

  int size() {
    return size;
  }

  /** A copy of everything written, independent of this writer's buffer. */
  byte[] toByteArray() {
    return size == bytes.length ? bytes.clone() : Arrays.copyOf(bytes, size);
  }

  /** Everything written, gzipped, without first copying it out. */
  byte[] gzip() {
    ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, size / 4));
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(bytes, 0, size);
    } catch (IOException e) {
      throw new IllegalStateException(e); // writing to memory does not fail
    }
    return out.toByteArray();
  }

  /** What has been written, as a string. For small payloads and tests, not the image path. */
  @Override
  public String toString() {
    return new String(bytes, 0, size, StandardCharsets.UTF_8);
  }

  // -- structure --

  JsonByteWriter beginObject() {
    separate();
    append('{');
    needsSeparator = false;
    return this;
  }

  JsonByteWriter endObject() {
    append('}');
    needsSeparator = true;
    return this;
  }

  JsonByteWriter beginArray() {
    separate();
    append('[');
    needsSeparator = false;
    return this;
  }

  JsonByteWriter endArray() {
    append(']');
    needsSeparator = true;
    return this;
  }

  JsonByteWriter name(String name) {
    separate();
    writeQuoted(name);
    append(':');
    needsSeparator = false;
    return this;
  }

  // -- values --

  JsonByteWriter value(String value) {
    if (value == null) {
      return nullValue();
    }
    separate();
    writeQuoted(value);
    needsSeparator = true;
    return this;
  }

  JsonByteWriter value(long value) {
    separate();
    writeLong(value);
    needsSeparator = true;
    return this;
  }

  /** A number; one that JSON cannot hold, infinite or NaN, is written as null. */
  JsonByteWriter value(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return nullValue();
    }
    if (value == Math.rint(value) && Math.abs(value) < 1e15) {
      return value((long) value);
    }
    separate();
    appendAscii(Double.toString(value));
    needsSeparator = true;
    return this;
  }

  JsonByteWriter value(boolean value) {
    separate();
    append(value ? TRUE : FALSE);
    needsSeparator = true;
    return this;
  }

  JsonByteWriter nullValue() {
    separate();
    append(NULL);
    needsSeparator = true;
    return this;
  }

  /**
   * A string made of {@code prefix}, which is escaped, then {@code ascii}, copied as it is. For base64
   * data, which is ASCII and never needs escaping, so it never becomes a string.
   */
  JsonByteWriter asciiValue(String prefix, byte[] ascii) {
    separate();
    writeQuoted(prefix);
    size--; // reopen the string the prefix closed
    ensure(ascii.length + 1L);
    System.arraycopy(ascii, 0, bytes, size, ascii.length);
    size += ascii.length;
    bytes[size++] = '"';
    needsSeparator = true;
    return this;
  }

  /** JSON encoded earlier, written as it is in the place of a value. */
  JsonByteWriter raw(byte[] json) {
    separate();
    ensure(json.length);
    System.arraycopy(json, 0, bytes, size, json.length);
    size += json.length;
    needsSeparator = true;
    return this;
  }

  /** JSON encoded earlier as a string, written as it is in the place of a value. */
  JsonByteWriter raw(String json) {
    return raw(json.getBytes(StandardCharsets.UTF_8));
  }

  /** A string field, left out when the value is null. */
  JsonByteWriter optional(String name, String value) {
    return value == null ? this : name(name).value(value);
  }

  /** An object of string values. */
  JsonByteWriter stringMap(Map<String, String> map) {
    beginObject();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      name(entry.getKey()).value(entry.getValue());
    }
    return endObject();
  }

  /** A string as a JSON string literal. */
  static String quoted(String s) {
    return new JsonByteWriter(s.length() + 2L).value(s).toString();
  }

  // -- primitives --

  private void separate() {
    if (needsSeparator) {
      append(',');
    }
  }

  private void append(char ascii) {
    ensure(1);
    bytes[size++] = (byte) ascii;
  }

  private void append(byte[] literal) {
    ensure(literal.length);
    System.arraycopy(literal, 0, bytes, size, literal.length);
    size += literal.length;
  }

  private void appendAscii(String ascii) {
    int length = ascii.length();
    ensure(length);
    for (int i = 0; i < length; i++) {
      bytes[size++] = (byte) ascii.charAt(i);
    }
  }

  /** Digits are written backwards and then flipped in place, so a number allocates nothing. */
  private void writeLong(long value) {
    if (value == Long.MIN_VALUE) {
      append(LONG_MIN_VALUE);
      return;
    }
    ensure(20);
    if (value < 0) {
      bytes[size++] = '-';
      value = -value;
    }
    if (value == 0) {
      bytes[size++] = '0';
      return;
    }
    int start = size;
    while (value > 0) {
      bytes[size++] = (byte) ('0' + (int) (value % 10));
      value /= 10;
    }
    for (int lower = start, upper = size - 1; lower < upper; lower++, upper--) {
      byte swap = bytes[lower];
      bytes[lower] = bytes[upper];
      bytes[upper] = swap;
    }
  }

  /**
   * Escapes every control character, {@code "} and {@code \}, and U+2028 and U+2029, which some
   * JavaScript parsers take as line ends; encodes the rest as UTF-8, with a surrogate missing its
   * other half written as {@code ?}, as {@code String.getBytes} would.
   */
  private void writeQuoted(String value) {
    int length = value.length();
    // Six bytes is the most any one char takes: an escape such as \u001f.
    ensure(length * 6L + 2);
    byte[] b = bytes;
    int n = size;
    b[n++] = '"';
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      if (c < 0x80) {
        if (c >= 0x20 && c != '"' && c != '\\') {
          b[n++] = (byte) c;
        } else {
          n = writeEscape(b, n, c);
        }
      } else if (c < 0x800) {
        b[n++] = (byte) (0xC0 | (c >> 6));
        b[n++] = (byte) (0x80 | (c & 0x3F));
      } else if (Character.isSurrogate(c)) {
        if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(value.charAt(i + 1))) {
          int codePoint = Character.toCodePoint(c, value.charAt(++i));
          b[n++] = (byte) (0xF0 | (codePoint >> 18));
          b[n++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
          b[n++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
          b[n++] = (byte) (0x80 | (codePoint & 0x3F));
        } else {
          b[n++] = '?';
        }
      } else if (c == ' ' || c == ' ') {
        n = writeUnicodeEscape(b, n, c);
      } else {
        b[n++] = (byte) (0xE0 | (c >> 12));
        b[n++] = (byte) (0x80 | ((c >> 6) & 0x3F));
        b[n++] = (byte) (0x80 | (c & 0x3F));
      }
    }
    b[n++] = '"';
    size = n;
  }

  private static int writeEscape(byte[] b, int n, char c) {
    b[n++] = '\\';
    switch (c) {
      case '"':
        b[n++] = '"';
        return n;
      case '\\':
        b[n++] = '\\';
        return n;
      case '\n':
        b[n++] = 'n';
        return n;
      case '\r':
        b[n++] = 'r';
        return n;
      case '\t':
        b[n++] = 't';
        return n;
      default:
        return writeUnicodeEscape(b, n - 1, c);
    }
  }

  private static int writeUnicodeEscape(byte[] b, int n, char c) {
    b[n++] = '\\';
    b[n++] = 'u';
    b[n++] = HEX_DIGITS[(c >> 12) & 0xF];
    b[n++] = HEX_DIGITS[(c >> 8) & 0xF];
    b[n++] = HEX_DIGITS[(c >> 4) & 0xF];
    b[n++] = HEX_DIGITS[c & 0xF];
    return n;
  }

  private void ensure(long additional) {
    long required = size + additional;
    if (required > bytes.length) {
      if (required > MAX_CAPACITY) {
        throw new IllegalStateException("JSON too large: " + required + " bytes");
      }
      bytes = Arrays.copyOf(bytes, (int) Math.min(MAX_CAPACITY, Math.max(required, bytes.length * 2L)));
    }
  }
}
