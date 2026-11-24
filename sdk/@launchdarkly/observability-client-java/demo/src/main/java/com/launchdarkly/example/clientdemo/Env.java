package com.launchdarkly.example.clientdemo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Settings from the environment, falling back to a {@code .env} file in the
 * working directory.
 *
 * <p>{@code ./gradlew run} already passes {@code .env} as environment
 * variables. This fallback is for {@code java -jar}, so someone given the jar
 * can drop a {@code .env} next to it instead of exporting variables.
 */
public final class Env {

    private static final Map<String, String> DOT_ENV = load(Path.of(".env"));

    private Env() {
        throw new AssertionError("No instances");
    }

    /** The environment variable, else the {@code .env} entry, if either is set. */
    public static Optional<String> get(String name) {
        return Optional.ofNullable(System.getenv(name))
                .or(() -> Optional.ofNullable(DOT_ENV.get(name)));
    }

    /** Like {@link #get}, with blank values treated as unset. */
    public static Optional<String> getNonBlank(String name) {
        return get(name).map(String::trim).filter(value -> !value.isEmpty());
    }

    /**
     * Parses {@code NAME=value} lines. Blank lines and {@code #} comments are
     * skipped and surrounding quotes are stripped, matching the Gradle build's
     * reader.
     */
    private static Map<String, String> load(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            System.out.println("  ! could not read .env: " + e.getMessage());
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            int equals = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || equals < 0) {
                continue;
            }
            String name = line.substring(0, equals).trim();
            String value = unquote(line.substring(equals + 1).trim());
            values.put(name, value);
        }
        return Map.copyOf(values);
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
