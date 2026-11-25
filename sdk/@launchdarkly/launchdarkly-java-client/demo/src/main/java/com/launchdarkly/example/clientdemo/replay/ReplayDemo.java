package com.launchdarkly.example.clientdemo.replay;

import com.launchdarkly.logging.LDSLF4J;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.Observability;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.PrivacyProfile;
import com.launchdarkly.observability.ReplayOptions;
import com.launchdarkly.observability.SessionReplay;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.client.Components;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.LDConfig;
import com.launchdarkly.example.clientdemo.Env;

import java.util.List;
import java.util.Optional;

/**
 * What the session replay demos share: one client, recording for session replay, and the content
 * each demo's window shows, so every toolkit records the same form.
 *
 * <p>Replay needs LaunchDarkly, so a demo records only with {@code LD_MOBILE_KEY} or
 * {@code LD_CLIENT_SIDE_ID} set, in the environment or {@code .env}. Without one it still shows its
 * window, and says it is not recording.
 */
public final class ReplayDemo {

    /** Seconds to wait for flag values before showing the window anyway. */
    private static final int START_WAIT_SECONDS = 5;

    /** The rows of each demo's list, which replay shows: the default masks only inputs. */
    public static final List<String> FLAGS = List.of(
            "dashboard                    on",
            "dashboard-progress-meters    on",
            "dashboard-line-chart         off",
            "dashboard-bar-chart          on",
            "new-checkout                 off",
            "dark-mode                    on");

    /** A note the demos mask by hand, with their toolkit's own mark, though the default would show it. */
    public static final String NOTE = "Account note: card ending 4242, renews 03/27";

    private final Optional<LDClient> client;

    private ReplayDemo(Optional<LDClient> client) {
        this.client = client;
    }

    /**
     * Starts the client, recording for session replay as {@code service}, when a credential is set.
     */
    public static ReplayDemo start(String service) {
        Optional<String> mobileKey = Env.getNonBlank("LD_MOBILE_KEY");
        Optional<String> clientSideId = Env.getNonBlank("LD_CLIENT_SIDE_ID");
        if (mobileKey.isEmpty() && clientSideId.isEmpty()) {
            System.out.println("  ! No LD_MOBILE_KEY or LD_CLIENT_SIDE_ID: showing the window without recording.");
            return new ReplayDemo(Optional.empty());
        }
        boolean debug = Env.getNonBlank("REPLAY_DEBUG").map(Boolean::parseBoolean).orElse(false);
        LDConfig.Builder builder = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
                .logAdapter(LDSLF4J.adapter())
                .applicationInfo(Components.applicationInfo().applicationId(service).applicationVersion("0.1.0"));
        mobileKey.ifPresentOrElse(builder::mobileKey, () -> builder.clientSideId(clientSideId.get()));
        builder.plugins(Components.plugins().setPlugins(List.of(
                new Observability(ObservabilityOptions.builder()
                        .serviceName(service)
                        .serviceVersion("0.1.0")
                        // Logback shows INFO and up; replay's batches are logged at DEBUG.
                        .logAdapter(debug ? Logs.toConsole() : LDSLF4J.adapter())
                        .build()),
                // Android's default: text fields and passwords masked, the rest shown.
                // REPLAY_DEBUG=true logs each batch of frames sent.
                new SessionReplay(ReplayOptions.builder()
                        .privacyProfile(PrivacyProfile.defaults())
                        .debug(debug)
                        .build()))));
        LDContext context = LDContext.builder(Env.getNonBlank("LD_CONTEXT_KEY").orElse("replay-demo@example.com"))
                .name("Replay demo")
                .build();
        LDClient client = LDClient.init(builder.build(), context, START_WAIT_SECONDS);
        System.out.println("  Recording session " + LDObserve.getSessionId()
                + ". Watch it under Sessions in LaunchDarkly Observability; it can take a few minutes to appear.");
        return new ReplayDemo(Optional.of(client));
    }

    /** What the window's status line says. */
    public String status() {
        return client.isPresent() ? "Recording for session replay: session " + LDObserve.getSessionId()
                : "Not recording: set LD_MOBILE_KEY or LD_CLIENT_SIDE_ID in .env";
    }

    /** The Save button: a custom event, which shows on the replay's timeline. */
    public void save() {
        client.ifPresent(c -> c.track("replay-demo-save"));
    }

    /** Sends what is queued and closes the client. */
    public void stop() {
        client.ifPresent(c -> {
            try {
                c.close();
            } catch (java.io.IOException e) {
                System.out.println("  ! closing the client: " + e.getMessage());
            }
            LDObserve.shutdown();
        });
    }
}
