package com.launchdarkly.example.clientdemo.dashboard;

import com.launchdarkly.logging.LDSLF4J;
import com.launchdarkly.observability.Observability;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.PrivacyProfile;
import com.launchdarkly.observability.ReplayOptions;
import com.launchdarkly.observability.SessionReplay;
import com.launchdarkly.sdk.client.Components;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.LDConfig;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Swing dashboard for the LaunchDarkly client-side Java SDK.
 *
 * <p>Each dashboard component is behind a flag, and the window updates the
 * moment a flag changes. With a mobile key or client-side ID it streams from
 * LaunchDarkly; with neither it runs offline, with on-screen switches driving
 * the SDK's TestData source.
 */
public final class DashboardApp {

    /** How long startup waits for flag values before opening the window anyway. */
    private static final int START_WAIT_SECONDS = 5;

    private static final String APPLICATION_ID = "launchdarkly-java-client-demo-dashboard";

    private DashboardApp() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("The dashboard needs a display. Run the console demo instead.");
            System.exit(2);
            return;
        }
        Theme.install();

        AtomicReference<Optional<DashboardSettings>> answer = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                answer.set(SetupDialog.ask(DashboardSettings.fromEnvironment())));
        Optional<DashboardSettings> chosen = answer.get();
        if (chosen.isEmpty()) {
            System.exit(0);
            return;
        }
        DashboardSettings settings = chosen.get();

        Optional<OfflineFlags> offline =
                settings.isLive() ? Optional.empty() : Optional.of(new OfflineFlags());

        System.out.printf("Starting the client (%s) for %s ...%n",
                settings.credentialDescription(), settings.email());
        // Off the Swing thread: this blocks for up to START_WAIT_SECONDS while the
        // first flag values arrive, then carries on in the background.
        LDClient client = LDClient.init(ldConfig(settings, offline), settings.context(),
                START_WAIT_SECONDS);
        if (settings.isLive() && !client.isInitialized()) {
            System.out.printf("  ! no flag values within %ds; the SDK keeps trying.%n",
                    START_WAIT_SECONDS);
        }

        SwingUtilities.invokeLater(() ->
                new DashboardWindow(client, settings, offline).setVisible(true));
    }

    private static LDConfig ldConfig(DashboardSettings settings, Optional<OfflineFlags> offline) {
        LDConfig.Builder builder = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
                .logAdapter(LDSLF4J.adapter())
                .applicationInfo(Components.applicationInfo()
                        .applicationId(APPLICATION_ID)
                        .applicationVersion("0.1.0"));

        settings.credential().ifPresent(credential -> {
            if (settings.isMobileKey()) {
                builder.mobileKey(credential);
            } else {
                builder.clientSideId(credential);
            }
        });

        if (settings.isLive()) {
            // Sends flag evaluations, identifies, errors and crashes to LaunchDarkly
            // Observability, and records the window for session replay, with text fields
            // and passwords masked. Live mode only: offline, the credential is a placeholder.
            builder.plugins(Components.plugins().setPlugins(List.of(
                    new Observability(ObservabilityOptions.builder()
                            .serviceName(APPLICATION_ID)
                            .serviceVersion("0.1.0")
                            .logAdapter(LDSLF4J.adapter())
                            .build()),
                    new SessionReplay(ReplayOptions.builder()
                            .privacyProfile(PrivacyProfile.defaults())
                            .build()))));
        }

        offline.ifPresent(flags -> builder
                // The SDK needs a credential even when no request is ever made.
                .mobileKey("offline-demo")
                .dataSource(flags.dataSource())
                .events(Components.noEvents())
                .disablePersistence());

        return builder.build();
    }
}
