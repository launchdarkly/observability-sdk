package com.launchdarkly.observability.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The process-wide attributes added to every span, log record and metric the plugin records. Held
 * as one immutable snapshot, so readers pay a volatile read and nothing else. It lives outside the
 * service so values set before the plugin starts apply once it does, and so they survive a restart.
 * <p>
 * Keys are matched by name: setting {@code "tier"} as a long replaces a {@code "tier"} string, and
 * a span or record that already has a {@code "tier"} of any type keeps it. The session keys are
 * reserved, as on Android, so a global attribute cannot spoof the session.
 */
public final class GlobalAttributes {
  /** Keys owned by session management, which global attributes may not set. */
  static final Set<String> RESERVED_KEYS = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList(Constants.SESSION_ID_ATTRIBUTE, "highlight.session_id")));

  private static final AtomicReference<Attributes> CURRENT = new AtomicReference<>(Attributes.empty());

  private GlobalAttributes() {}

  /** The current global attributes. Never null. */
  public static Attributes get() {
    return CURRENT.get();
  }

  /**
   * Merges {@code attributes} into the global set; their values replace those of keys already
   * present. Reserved keys are dropped.
   *
   * @return the reserved key names that were dropped, so the caller can report them
   */
  public static Set<String> set(Attributes attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> dropped = new HashSet<>();
    AttributesBuilder accepted = attributes.toBuilder();
    accepted.removeIf(key -> {
      boolean reserved = RESERVED_KEYS.contains(key.getKey());
      if (reserved) {
        dropped.add(key.getKey());
      }
      return reserved;
    });
    Attributes add = accepted.build();
    if (!add.isEmpty()) {
      Set<String> names = names(add);
      CURRENT.updateAndGet(current -> {
        AttributesBuilder builder = current.toBuilder();
        builder.removeIf(key -> names.contains(key.getKey()));
        return builder.putAll(add).build();
      });
    }
    return dropped;
  }

  /** Removes the attributes with these key names. Unknown names and a null collection are ignored. */
  public static void remove(Collection<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    Set<String> names = new HashSet<>(keys);
    CURRENT.updateAndGet(current -> {
      AttributesBuilder builder = current.toBuilder();
      builder.removeIf(key -> names.contains(key.getKey()));
      return builder.build();
    });
  }

  /** Returns {@code attributes} with the global attributes added for every key name it lacks. */
  public static Attributes merge(Attributes attributes) {
    Attributes globals = CURRENT.get();
    if (attributes == null || attributes.isEmpty()) {
      return globals;
    }
    if (globals.isEmpty()) {
      return attributes;
    }
    return attributes.toBuilder().putAll(missingFrom(globals, attributes)).build();
  }

  /** The entries of {@code globals} whose key name is not in {@code existing}. */
  static Attributes missingFrom(Attributes globals, Attributes existing) {
    if (existing.isEmpty()) {
      return globals;
    }
    Set<String> present = names(existing);
    AttributesBuilder builder = globals.toBuilder();
    builder.removeIf(key -> present.contains(key.getKey()));
    return builder.build();
  }

  private static Set<String> names(Attributes attributes) {
    Set<String> names = new HashSet<>();
    for (AttributeKey<?> key : attributes.asMap().keySet()) {
      names.add(key.getKey());
    }
    return names;
  }

  /** Drops every global attribute. For tests. */
  public static void clearForTest() {
    CURRENT.set(Attributes.empty());
  }
}
