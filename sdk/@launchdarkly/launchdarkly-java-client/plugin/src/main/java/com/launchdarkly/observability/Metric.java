package com.launchdarkly.observability;

import io.opentelemetry.api.common.Attributes;

import java.util.Objects;

/**
 * A value to record with one of the {@code LDObserve.record...} metric methods.
 */
public final class Metric {
  private final String name;
  private final double value;
  private final Attributes attributes;

  /**
   * Creates a metric with no attributes.
   *
   * @param name the metric name
   * @param value the value
   */
  public Metric(String name, double value) {
    this(name, value, Attributes.empty());
  }

  /**
   * Creates a metric.
   *
   * @param name the metric name
   * @param value the value
   * @param attributes the attributes of this data point; null means none
   */
  public Metric(String name, double value, Attributes attributes) {
    this.name = Objects.requireNonNull(name, "name");
    this.value = value;
    this.attributes = attributes == null ? Attributes.empty() : attributes;
  }

  /**
   * The metric name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * The value. Counters and up/down counters take its whole part.
   *
   * @return the value
   */
  public double getValue() {
    return value;
  }

  /**
   * The attributes of this data point. Never null.
   *
   * @return the attributes
   */
  public Attributes getAttributes() {
    return attributes;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Metric)) {
      return false;
    }
    Metric o = (Metric) other;
    return name.equals(o.name) && Double.compare(value, o.value) == 0 && attributes.equals(o.attributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value, attributes);
  }

  @Override
  public String toString() {
    return "Metric(name=" + name + ", value=" + value + ", attributes=" + attributes + ")";
  }
}
