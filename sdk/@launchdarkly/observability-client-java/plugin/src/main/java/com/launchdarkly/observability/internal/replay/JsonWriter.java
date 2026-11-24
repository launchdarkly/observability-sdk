package com.launchdarkly.observability.internal.replay;

import java.util.Map;

/**
 * Writes compact JSON into a string. Replay payloads carry megabytes of base64 image data, which a
 * JSON tree would copy several times over; this writes each string once.
 * <p>
 * The caller is trusted to nest calls correctly: it is an internal tool with a handful of callers,
 * not a general library.
 */
final class JsonWriter {
  private final StringBuilder out;
  // Whether the container being written already has an element, so the next one needs a comma.
  private boolean[] hasElement = new boolean[16];
  private int depth;
  private boolean afterName;

  JsonWriter() {
    this(256);
  }

  JsonWriter(int capacity) {
    out = new StringBuilder(capacity);
  }

  JsonWriter beginObject() {
    beforeValue();
    out.append('{');
    push();
    return this;
  }

  JsonWriter endObject() {
    depth--;
    out.append('}');
    return this;
  }

  JsonWriter beginArray() {
    beforeValue();
    out.append('[');
    push();
    return this;
  }

  JsonWriter endArray() {
    depth--;
    out.append(']');
    return this;
  }

  JsonWriter name(String name) {
    beforeValue();
    quote(name);
    out.append(':');
    afterName = true;
    return this;
  }

  JsonWriter value(String value) {
    beforeValue();
    if (value == null) {
      out.append("null");
    } else {
      quote(value);
    }
    return this;
  }

  JsonWriter value(long value) {
    beforeValue();
    out.append(value);
    return this;
  }

  /** A number; one that JSON cannot hold, infinite or NaN, is written as null. */
  JsonWriter value(double value) {
    beforeValue();
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      out.append("null");
    } else if (value == Math.rint(value) && Math.abs(value) < 1e15) {
      out.append((long) value);
    } else {
      out.append(value);
    }
    return this;
  }

  JsonWriter value(boolean value) {
    beforeValue();
    out.append(value);
    return this;
  }

  /** Already-encoded JSON, written as it is. */
  JsonWriter raw(String json) {
    beforeValue();
    out.append(json);
    return this;
  }

  /** A string field, left out when the value is null. */
  JsonWriter optional(String name, String value) {
    return value == null ? this : name(name).value(value);
  }

  /** An object of string values. */
  JsonWriter stringMap(Map<String, String> map) {
    beginObject();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      name(entry.getKey()).value(entry.getValue());
    }
    return endObject();
  }

  @Override
  public String toString() {
    return out.toString();
  }

  private void push() {
    if (depth == hasElement.length) {
      boolean[] grown = new boolean[depth * 2];
      System.arraycopy(hasElement, 0, grown, 0, depth);
      hasElement = grown;
    }
    hasElement[depth++] = false;
  }

  private void beforeValue() {
    if (afterName) {
      afterName = false;
      return;
    }
    if (depth > 0) {
      if (hasElement[depth - 1]) {
        out.append(',');
      }
      hasElement[depth - 1] = true;
    }
  }

  private void quote(String s) {
    out.append('"');
    int start = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      String escape;
      if (c == '"') {
        escape = "\\\"";
      } else if (c == '\\') {
        escape = "\\\\";
      } else if (c == '\n') {
        escape = "\\n";
      } else if (c == '\r') {
        escape = "\\r";
      } else if (c == '\t') {
        escape = "\\t";
      } else if (c < 0x20 || c == ' ' || c == ' ') {
        escape = String.format("\\u%04x", (int) c);
      } else {
        continue;
      }
      out.append(s, start, i).append(escape);
      start = i + 1;
    }
    out.append(s, start, s.length()).append('"');
  }

  /** A string as a JSON string literal. */
  static String quoted(String s) {
    return new JsonWriter(s.length() + 2).value(s).toString();
  }
}
