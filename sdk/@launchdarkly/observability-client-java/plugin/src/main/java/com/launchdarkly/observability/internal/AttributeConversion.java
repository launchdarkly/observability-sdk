package com.launchdarkly.observability.internal;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns plain values into OpenTelemetry attributes, with the Android plugin's rules.
 */
public final class AttributeConversion {
  /**
   * How deep nested maps and lists are followed. Deeper values are skipped, which also stops a map
   * that contains itself from overflowing the stack.
   */
  static final int MAX_DEPTH = 16;

  private AttributeConversion() {}

  /**
   * Converts a {@code track} event's data. Only an object has members to convert; each boolean,
   * number or string member becomes an attribute, and null, array and object members are skipped,
   * because attributes are flat. Numbers become doubles, the only number type {@link LDValue} has.
   */
  public static Attributes fromLDValue(LDValue value) {
    if (value == null || value.getType() != LDValueType.OBJECT) {
      return Attributes.empty();
    }
    AttributesBuilder builder = Attributes.builder();
    for (String key : value.keys()) {
      LDValue member = value.get(key);
      switch (member.getType()) {
        case BOOLEAN:
          builder.put(AttributeKey.booleanKey(key), member.booleanValue());
          break;
        case NUMBER:
          builder.put(AttributeKey.doubleKey(key), member.doubleValue());
          break;
        case STRING:
          builder.put(AttributeKey.stringKey(key), member.stringValue());
          break;
        default:
          break;
      }
    }
    return builder.build();
  }

  /**
   * Converts a map of plain values, keeping as much structure as flat attributes allow:
   * <ul>
   *   <li>booleans and strings stay as they are; {@code Byte}, {@code Short}, {@code Integer} and
   *   {@code Long} become longs, and {@code Float} and {@code Double} become doubles;</li>
   *   <li>a list whose elements are all strings, all booleans, all integers or all numbers becomes
   *   an array attribute;</li>
   *   <li>nested maps are flattened with dotted keys ({@code user.id}), and other lists with indexed
   *   keys ({@code items.0.id});</li>
   *   <li>an {@link Attributes} value is merged in under its key as a prefix, keeping its types;</li>
   *   <li>anything else, null included, is skipped, never turned into a string, as is anything
   *   nested more than {@value #MAX_DEPTH} levels deep.</li>
   * </ul>
   * A null map gives empty attributes.
   */
  public static Attributes fromMap(Map<String, ?> properties) {
    if (properties == null || properties.isEmpty()) {
      return Attributes.empty();
    }
    AttributesBuilder builder = Attributes.builder();
    putMap(builder, null, properties, 0);
    return builder.build();
  }

  private static void putMap(AttributesBuilder builder, String prefix, Map<?, ?> source, int depth) {
    if (depth > MAX_DEPTH) {
      return;
    }
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      String name = entry.getKey().toString();
      putValue(builder, prefix == null ? name : prefix + "." + name, entry.getValue(), depth);
    }
  }

  private static void putValue(AttributesBuilder builder, String key, Object value, int depth) {
    if (value instanceof Boolean) {
      builder.put(AttributeKey.booleanKey(key), (Boolean) value);
    } else if (isInteger(value)) {
      builder.put(AttributeKey.longKey(key), ((Number) value).longValue());
    } else if (value instanceof Float || value instanceof Double) {
      builder.put(AttributeKey.doubleKey(key), ((Number) value).doubleValue());
    } else if (value instanceof String) {
      builder.put(AttributeKey.stringKey(key), (String) value);
    } else if (value instanceof Attributes) {
      ((Attributes) value).forEach((attributeKey, attributeValue) -> putUnchecked(builder,
          rekey(attributeKey, key + "." + attributeKey.getKey()), attributeValue));
    } else if (value instanceof Map) {
      putMap(builder, key, (Map<?, ?>) value, depth + 1);
    } else if (value instanceof List) {
      putList(builder, key, (List<?>) value, depth + 1);
    }
  }

  private static void putList(AttributesBuilder builder, String key, List<?> list, int depth) {
    if (list.isEmpty() || depth > MAX_DEPTH) {
      return;
    }
    if (all(list, String.class)) {
      List<String> strings = new ArrayList<>();
      list.forEach(element -> strings.add((String) element));
      builder.put(AttributeKey.stringArrayKey(key), strings);
    } else if (all(list, Boolean.class)) {
      List<Boolean> booleans = new ArrayList<>();
      list.forEach(element -> booleans.add((Boolean) element));
      builder.put(AttributeKey.booleanArrayKey(key), booleans);
    } else if (allIntegers(list)) {
      List<Long> longs = new ArrayList<>();
      list.forEach(element -> longs.add(((Number) element).longValue()));
      builder.put(AttributeKey.longArrayKey(key), longs);
    } else if (allFloatingOrInteger(list)) {
      List<Double> doubles = new ArrayList<>();
      list.forEach(element -> doubles.add(((Number) element).doubleValue()));
      builder.put(AttributeKey.doubleArrayKey(key), doubles);
    } else {
      for (int i = 0; i < list.size(); i++) {
        putValue(builder, key + "." + i, list.get(i), depth);
      }
    }
  }

  private static boolean isInteger(Object value) {
    return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
  }

  private static boolean all(List<?> list, Class<?> type) {
    for (Object element : list) {
      if (!type.isInstance(element)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allIntegers(List<?> list) {
    for (Object element : list) {
      if (!isInteger(element)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allFloatingOrInteger(List<?> list) {
    for (Object element : list) {
      if (!isInteger(element) && !(element instanceof Float) && !(element instanceof Double)) {
        return false;
      }
    }
    return true;
  }

  private static AttributeKey<?> rekey(AttributeKey<?> original, String name) {
    AttributeType type = original.getType();
    switch (type) {
      case BOOLEAN:
        return AttributeKey.booleanKey(name);
      case LONG:
        return AttributeKey.longKey(name);
      case DOUBLE:
        return AttributeKey.doubleKey(name);
      case STRING_ARRAY:
        return AttributeKey.stringArrayKey(name);
      case BOOLEAN_ARRAY:
        return AttributeKey.booleanArrayKey(name);
      case LONG_ARRAY:
        return AttributeKey.longArrayKey(name);
      case DOUBLE_ARRAY:
        return AttributeKey.doubleArrayKey(name);
      default:
        return AttributeKey.stringKey(name);
    }
  }

  @SuppressWarnings("unchecked")
  private static void putUnchecked(AttributesBuilder builder, AttributeKey<?> key, Object value) {
    builder.put((AttributeKey<Object>) key, value);
  }
}
