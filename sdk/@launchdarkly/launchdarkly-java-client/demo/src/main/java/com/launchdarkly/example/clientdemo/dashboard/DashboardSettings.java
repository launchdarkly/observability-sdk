package com.launchdarkly.example.clientdemo.dashboard;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.example.clientdemo.Env;
import java.util.Optional;

/**
 * What the dashboard connects with and who it evaluates flags for.
 *
 * <p>{@code credential} is a mobile key or a client-side ID; mobile keys start
 * with {@code mob-}, so the kind is worked out from the value. Empty means
 * offline.
 */
record DashboardSettings(Optional<String> credential, String email, String name) {

    static final String DEFAULT_EMAIL = "demo-user@example.com";
    static final String DEFAULT_NAME = "Demo User";

    /** Pre-filled from {@code LD_MOBILE_KEY} or {@code LD_CLIENT_SIDE_ID}, then the user. */
    static DashboardSettings fromEnvironment() {
        Optional<String> credential = Env.getNonBlank("LD_MOBILE_KEY")
                .or(() -> Env.getNonBlank("LD_CLIENT_SIDE_ID"));
        return new DashboardSettings(
                credential,
                Env.getNonBlank("LD_USER_EMAIL").orElse(DEFAULT_EMAIL),
                Env.getNonBlank("LD_USER_NAME").orElse(DEFAULT_NAME));
    }

    boolean isLive() {
        return credential.isPresent();
    }

    boolean isMobileKey() {
        return credential.map(value -> value.startsWith("mob-")).orElse(false);
    }

    String credentialDescription() {
        if (!isLive()) {
            return "offline";
        }
        return isMobileKey() ? "mobile key" : "client-side ID";
    }

    /** A user context keyed by email, as the original dashboard used. */
    LDContext context() {
        return userContext(email, name);
    }

    static LDContext userContext(String email, String name) {
        return LDContext.builder(email)
                .name(name)
                .set("email", email)
                .build();
    }
}
