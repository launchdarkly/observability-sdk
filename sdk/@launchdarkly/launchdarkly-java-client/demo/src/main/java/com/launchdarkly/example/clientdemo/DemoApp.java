package com.launchdarkly.example.clientdemo;

import com.launchdarkly.logging.LDSLF4J;
import com.launchdarkly.observability.Observability;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.client.Components;
import com.launchdarkly.sdk.client.ConnectionInformation;
import com.launchdarkly.sdk.client.IdentifyResult;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.LDConfig;
import com.launchdarkly.sdk.client.LDFailure;
import com.launchdarkly.sdk.client.LDStatusListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sample app for the LaunchDarkly client-side Java SDK.
 *
 * <p>It prints the watched flags once, then reports each change the moment the
 * SDK delivers it, so changing a flag in LaunchDarkly visibly changes the output
 * of a running process. {@code --ticks} adds a snapshot on a timer. Type {@code h} for the commands, which cover
 * identify, track, offline and flush.
 *
 * <ul>
 *   <li>With a mobile key or client-side ID, it streams from LaunchDarkly -
 *       change the flags in the UI and watch this process react.
 *   <li>With no credential, it drives the same flags locally through the SDK's
 *       TestData source, cycling through sample values.
 * </ul>
 *
 * <p>See {@code --help}.
 */
public final class DemoApp {

    /** How long startup waits for flag values before continuing without them. */
    private static final int START_WAIT_SECONDS = 5;

    private static final String APPLICATION_ID = "launchdarkly-java-client-demo-app";

    private DemoApp() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) throws Exception {
        DemoCli cli = DemoCli.parse(args);
        switch (cli.mode()) {
            case HELP -> {
                DemoCli.printUsage();
                return;
            }
            case INVALID -> {
                // parse() has already explained the problem.
                System.exit(2);
                return;
            }
            default -> { /* fall through to run or doctor */ }
        }

        DemoConfig config = DemoConfig.resolve(cli.overrides());
        if (config.hasConflictingCredentials()) {
            System.out.println("Set LD_MOBILE_KEY or LD_CLIENT_SIDE_ID, not both.");
            System.exit(2);
            return;
        }

        Optional<FlagCycle> cycle =
                config.isLive() ? Optional.empty() : Optional.of(FlagCycle.seeded(config));

        boolean healthy = true;
        // Blocks for up to START_WAIT_SECONDS while the first flag values arrive,
        // then carries on in the background if they haven't.
        try (LDClient client = LDClient.init(ldConfig(config, cycle), config.context(),
                START_WAIT_SECONDS)) {

            if (cli.mode() == DemoCli.Mode.DOCTOR) {
                healthy = DemoDoctor.report(config, client);
            } else {
                run(config, cycle, client, cli.interactive());
            }
        }
        if (!healthy) {
            System.exit(1);
        }
        if (cli.mode() == DemoCli.Mode.RUN) {
            System.out.println(" Done.");
        }
    }

    private static void run(DemoConfig config, Optional<FlagCycle> cycle, LDClient client,
            boolean interactive) throws InterruptedException {

        for (String key : config.flagKeys()) {
            client.registerFeatureFlagListener(key, changed ->
                    printFlushed(">> %s changed: %s%n", changed, Flags.describe(client, changed)));
        }
        client.registerStatusListener(new StatusPrinter());

        printBanner(config, client, interactive);
        int ticks = runUntilStopped(config, cycle, client, interactive);

        System.out.println();
        System.out.println("-".repeat(72));
        System.out.println(" Shutting down");
        if (config.ticks()) {
            System.out.printf("   ticks            %d%n", ticks);
        }
        System.out.println("   closing the client, which sends any pending events");
        System.out.println("-".repeat(72));
        System.out.flush();
    }

    private static LDConfig ldConfig(DemoConfig config, Optional<FlagCycle> cycle) {
        LDConfig.Builder builder = new LDConfig.Builder(config.autoEnvAttributes()
                        ? LDConfig.Builder.AutoEnvAttributes.Enabled
                        : LDConfig.Builder.AutoEnvAttributes.Disabled)
                // Routes SDK logging into SLF4J, so it shares the Logback console
                // output and its format with the rest of the app.
                .logAdapter(LDSLF4J.adapter())
                // Lets the demo show why each flag has the value it has.
                .evaluationReasons(true)
                .applicationInfo(Components.applicationInfo()
                        .applicationId(APPLICATION_ID)
                        .applicationVersion("0.1.0"));

        config.mobileKey().ifPresent(builder::mobileKey);
        config.clientSideId().ifPresent(builder::clientSideId);

        if (config.isLive()) {
            // Sends flag evaluations, identifies, errors and crashes to LaunchDarkly
            // Observability. Live mode only: offline, the credential is a placeholder.
            builder.plugins(Components.plugins().setPlugins(List.of(new Observability(
                    ObservabilityOptions.builder()
                            .serviceName(APPLICATION_ID)
                            .serviceVersion("0.1.0")
                            .logAdapter(LDSLF4J.adapter())
                            .build()))));
        }

        cycle.ifPresent(offline -> builder
                // The SDK needs a credential even when no request is ever made.
                .mobileKey("offline-demo")
                .dataSource(offline.dataSource())
                .events(Components.noEvents())
                .disablePersistence());

        return builder.build();
    }

    /**
     * Runs the tick loop until stopped.
     *
     * @return how many ticks completed
     */
    private static int runUntilStopped(
            DemoConfig config,
            Optional<FlagCycle> cycle,
            LDClient client,
            boolean interactive)
            throws InterruptedException {

        CountDownLatch stop = new CountDownLatch(1);
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop.countDown();
            try {
                // Give main a moment to close the client cleanly.
                mainThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        if (interactive) {
            startCommandReader(config, client, stop);
        }

        // One snapshot to start from. After this, changes arrive as '>>' lines
        // from the flag listeners, and 'l' prints a fresh snapshot on demand.
        printFlushed("%n--- flags | %s ---%n", client.getConnectionInformation()
                .getConnectionMode());
        Flags.printWatched(client, config.flagKeys());

        // Live with ticks off there is nothing to do on a timer, so just wait.
        if (cycle.isEmpty() && !config.ticks()) {
            stop.await();
            return 0;
        }

        // Runs until Ctrl-C, SIGTERM, or the 'q' command.
        int ticks = 0;
        while (!stop.await(config.intervalSeconds(), TimeUnit.SECONDS)) {
            if (cycle.isPresent()) {
                // In offline mode, stand in for someone changing the flags in the
                // UI. The listeners report each change; waiting for it to land
                // keeps a tick from printing values about to change under it.
                FlagCycle offline = cycle.get();
                offline.advance();
                awaitApplied(config, client, offline);
            }
            if (config.ticks()) {
                ticks++;
                printFlushed("%n--- tick %d | %s ---%n", ticks, client.getConnectionInformation()
                        .getConnectionMode());
                Flags.printWatched(client, config.flagKeys());
            }
        }
        return ticks;
    }

    /**
     * Reads commands from the terminal on a daemon thread.
     *
     * <p>Degrades quietly when there is no terminal - piped input, or a CI run -
     * rather than sitting on a dead stream.
     */
    private static void startCommandReader(DemoConfig config, LDClient client,
            CountDownLatch stop) {
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = in.readLine()) != null) {
                    String trimmed = line.trim();
                    int space = trimmed.indexOf(' ');
                    String command = (space < 0 ? trimmed : trimmed.substring(0, space))
                            .toLowerCase();
                    String argument = space < 0 ? "" : trimmed.substring(space + 1).trim();
                    switch (command) {
                        case "l", "log" -> {
                            printFlushed("%n--- on demand ---%n");
                            Flags.printWatched(client, config.flagKeys());
                        }
                        case "a", "all" -> {
                            printFlushed("%n--- all flags ---%n");
                            Flags.printAll(client);
                        }
                        case "i", "identify" -> identify(config, client, argument);
                        case "t", "track" -> track(config, client, argument);
                        case "o", "offline" -> toggleOffline(client);
                        case "f", "flush" -> {
                            client.flush();
                            System.out.println(" flush requested");
                        }
                        case "s", "status" -> printStatus(config, client);
                        case "h", "help", "?" -> printCommands();
                        case "q", "quit", "exit" -> {
                            // The main loop prints the shutdown notice, so this
                            // only needs to signal it.
                            stop.countDown();
                            return;
                        }
                        case "" -> { /* bare Enter: ignore */ }
                        default -> System.out.printf(
                                "Unknown command '%s'. Type h for help.%n", trimmed);
                    }
                }
            } catch (IOException e) {
                System.out.println("! stopped reading commands: " + e.getMessage());
            }
        }, "demo-command-reader");

        reader.setDaemon(true);
        reader.start();
    }

    private static void identify(DemoConfig config, LDClient client, String key) {
        if (key.isEmpty()) {
            System.out.println(" usage: i KEY   (for example: i " + FlagCycle.TARGETED_KEY + ")");
            return;
        }
        System.out.printf(" identifying as %s:%s ...%n", config.contextKind(), key);
        try {
            IdentifyResult result = client.identify(config.contextWithKey(key))
                    .get(10, TimeUnit.SECONDS);
            System.out.printf(" identify %s%s%n", result.getStatus(),
                    result.getError() == null ? "" : ": " + result.getError().getMessage());
            Flags.printWatched(client, config.flagKeys());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            System.out.println(" ! identify failed: " + e);
        }
    }

    private static void track(DemoConfig config, LDClient client, String event) {
        if (event.isEmpty()) {
            System.out.println(" usage: t EVENT   (for example: t checkout-completed)");
            return;
        }
        client.track(event);
        System.out.printf(" tracked '%s'%s%n", event,
                config.isLive() ? "; it goes out with the next flush" : " (offline: not sent)");
    }

    private static void toggleOffline(LDClient client) {
        if (client.isOffline()) {
            client.setOnline();
            System.out.println(" back online");
        } else {
            client.setOffline();
            System.out.println(" offline: no requests, cached values keep serving");
        }
    }

    /**
     * Prints a line and flushes it.
     *
     * <p>{@code System.out} and Logback's console appender are two streams onto
     * the same terminal. Without an explicit flush, a banner can appear after the
     * log lines it introduces.
     */
    private static void printFlushed(String format, Object... args) {
        System.out.printf(format, args);
        System.out.flush();
    }

    private static void printStatus(DemoConfig config, LDClient client) {
        System.out.println();
        System.out.printf(" initialized         %s%n", client.isInitialized());
        System.out.printf(" offline             %s%n", client.isOffline());
        Flags.printConnection(client);
        System.out.printf(" context             %s%n", Flags.describeContext(client.getContext()));
        System.out.printf(" credential          %s%n", config.credentialDescription());
        System.out.printf(" SDK version         %s%n%n", client.getVersion());
    }

    private static void printCommands() {
        System.out.println("""

                 l          print the watched flags now
                 a          print every flag
                 i KEY      identify as another context (same kind and env)
                 t EVENT    track a custom event
                 o          go offline, or back online
                 f          flush analytics events
                 s          print connection status
                 h          this help
                 q          quit
                """);
    }

    /**
     * Waits for a TestData update to reach the client. Flag changes are delivered
     * on the SDK's own thread, so without this the tick could print values that
     * are about to change underneath it.
     */
    private static void awaitApplied(DemoConfig config, LDClient client, FlagCycle cycle)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            boolean applied = config.flagKeys().stream().allMatch(key -> Objects.equals(
                    cycle.expectedValue(key, client.getContext()),
                    client.jsonValueVariation(key, LDValue.ofNull())));
            if (applied) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        System.out.println(" ! timed out waiting for the flag change to be applied");
    }

    private static void printBanner(DemoConfig config, LDClient client, boolean interactive) {
        String mode = config.isLive()
                ? "live (" + config.credentialDescription() + ")"
                : "offline (SDK TestData - pass --mobile-key for live mode)";

        System.out.println("=".repeat(72));
        System.out.println(" LaunchDarkly client-side Java SDK - demo app");
        System.out.println("=".repeat(72));
        System.out.printf(" SDK version    %s%n", client.getVersion());
        System.out.printf(" mode           %s%n", mode);
        System.out.printf(" initialized    %s%n", client.isInitialized());
        System.out.printf(" context        %s (env=%s)%n",
                Flags.describeContext(client.getContext()), config.environment());
        System.out.printf(" watching       %s%n", String.join(", ", config.flagKeys()));
        if (config.ticks()) {
            System.out.printf(" ticks          every %ds%n", config.intervalSeconds());
        }
        if (!config.isLive()) {
            System.out.printf(" sample change  every %ds%n", config.intervalSeconds());
        }
        if (interactive) {
            System.out.println(" commands       l, a, i KEY, t EVENT, o, f, s, h, q  (h for help)");
        }
        System.out.println("-".repeat(72));
        if (config.isLive() && !client.isInitialized()) {
            System.out.printf(" ! No flag values within %ds. The SDK keeps trying in the background;%n",
                    START_WAIT_SECONDS);
            System.out.println("   run 'doctor' if they never arrive.");
            System.out.println("-".repeat(72));
        }
    }

    /** Reports connection changes and failures as the SDK sees them. */
    private static final class StatusPrinter implements LDStatusListener {
        @Override
        public void onConnectionModeChanged(ConnectionInformation info) {
            printFlushed(">> connection is now %s%n", info.getConnectionMode());
        }

        @Override
        public void onInternalFailure(LDFailure failure) {
            printFlushed(">> SDK failure %s: %s%n", failure.getFailureType(), failure.getMessage());
        }
    }
}
