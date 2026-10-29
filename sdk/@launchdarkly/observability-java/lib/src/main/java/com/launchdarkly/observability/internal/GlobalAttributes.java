package com.launchdarkly.observability.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The process-wide attributes added to every span, log record and
 * {@code LDObserve} metric. Held as one immutable snapshot, so readers on the
 * hot path pay a volatile read and nothing else. It lives outside
 * {@link OtelManager} so values set before the plugin starts apply once it
 * does.
 *
 * <p>Keys are matched by name: setting {@code "tier"} as a long replaces a
 * {@code "tier"} string, and a span or record that already has a {@code "tier"}
 * of any type keeps it.</p>
 */
public final class GlobalAttributes {

    private static final AtomicReference<Attributes> CURRENT = new AtomicReference<>(Attributes.empty());

    private GlobalAttributes() {}

    public static Attributes get() {
        return CURRENT.get();
    }

    /** Merges {@code attributes} into the global set; their values replace those of keys already present. */
    public static void set(Attributes attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        Set<String> names = names(attributes);
        CURRENT.updateAndGet(current -> {
            AttributesBuilder builder = current.toBuilder();
            builder.removeIf(key -> names.contains(key.getKey()));
            return builder.putAll(attributes).build();
        });
    }

    /** Removes the attributes with these key names. */
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

    /**
     * Returns {@code attributes} with the global attributes added for every key
     * name it does not already have.
     */
    public static Attributes merge(Attributes attributes) {
        Attributes globals = CURRENT.get();
        if (globals.isEmpty()) {
            return attributes != null ? attributes : Attributes.empty();
        }
        if (attributes == null || attributes.isEmpty()) {
            return globals;
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

    static void resetForTest() {
        CURRENT.set(Attributes.empty());
    }
}
