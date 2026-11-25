package com.launchdarkly.example.clientdemo;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.example.clientdemo.dashboard.DashboardFlags;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Demo settings. See {@code .env.example} and {@code --help}.
 *
 * <p>Each setting comes from a command-line override, else the environment
 * variable of the same name, else a built-in default.
 *
 * <p>With no credential the demo runs offline against the SDK's
 * {@code TestData} source, which still goes through the real flag-change path.
 */
record DemoConfig(
        Optional<String> mobileKey,
        Optional<String> clientSideId,
        List<String> flagKeys,
        String contextKind,
        String contextKey,
        String environment,
        boolean autoEnvAttributes,
        int intervalSeconds,
        boolean ticks) {

    static DemoConfig resolve(Map<String, String> overrides) {
        Settings settings = new Settings(overrides);
        return new DemoConfig(
                settings.get("LD_MOBILE_KEY").filter(key -> !key.isBlank()),
                settings.get("LD_CLIENT_SIDE_ID").filter(id -> !id.isBlank()),
                settings.get("LD_FLAG_KEYS")
                        .map(DemoConfig::splitKeys)
                        .filter(keys -> !keys.isEmpty())
                        // The dashboard's flags, so both demos show the same thing.
                        .orElse(DashboardFlags.ALL),
                settings.get("LD_CONTEXT_KIND").filter(k -> !k.isBlank()).orElse("user"),
                settings.get("LD_CONTEXT_KEY").filter(k -> !k.isBlank())
                        .orElse("demo-user@example.com"),
                settings.get("LD_ENV").filter(e -> !e.isBlank()).orElse("development"),
                settings.getBoolean("LD_AUTO_ENV_ATTRIBUTES", true),
                settings.getInt("DEMO_INTERVAL_SECONDS", 5),
                settings.getBoolean("DEMO_TICKS", false));
    }

    /** Whether to connect to LaunchDarkly rather than drive flags locally. */
    boolean isLive() {
        return mobileKey.isPresent() || clientSideId.isPresent();
    }

    /** Both credentials set: the SDK takes one, so the demo refuses to guess. */
    boolean hasConflictingCredentials() {
        return mobileKey.isPresent() && clientSideId.isPresent();
    }

    String credentialDescription() {
        if (mobileKey.isPresent()) {
            return "mobile key";
        }
        if (clientSideId.isPresent()) {
            return "client-side ID";
        }
        return "none (offline)";
    }

    /** The context flags are evaluated for at startup. */
    LDContext context() {
        return contextWithKey(contextKey);
    }

    /** The same kind and attributes with another key, for the identify command. */
    LDContext contextWithKey(String key) {
        return LDContext.builder(ContextKind.of(contextKind), key)
                .set("env", environment)
                .build();
    }

    private static List<String> splitKeys(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .distinct()
                .toList();
    }

    /** Command line first, then environment or {@code .env}, then the caller's default. */
    private record Settings(Map<String, String> overrides) {

        Optional<String> get(String name) {
            return Optional.ofNullable(overrides.get(name))
                    .or(() -> Env.get(name));
        }

        int getInt(String name, int fallback) {
            return get(name)
                    .filter(value -> !value.isBlank())
                    .map(value -> {
                        try {
                            return Integer.parseInt(value.trim());
                        } catch (NumberFormatException e) {
                            System.out.printf("  ! %s=%s is not a number, using %d%n",
                                    name, value, fallback);
                            return fallback;
                        }
                    })
                    .orElse(fallback);
        }

        boolean getBoolean(String name, boolean fallback) {
            return get(name)
                    .filter(value -> !value.isBlank())
                    .map(value -> switch (value.trim().toLowerCase()) {
                        case "true", "yes", "1", "on" -> true;
                        case "false", "no", "0", "off" -> false;
                        default -> {
                            System.out.printf("  ! %s=%s is not true or false, using %s%n",
                                    name, value, fallback);
                            yield fallback;
                        }
                    })
                    .orElse(fallback);
        }
    }
}
