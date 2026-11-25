package com.launchdarkly.example.clientdemo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Command-line parsing for the demo.
 *
 * <p>Every option maps onto the same setting name as its environment variable,
 * so there is a single resolution path: command line shadows environment, which
 * shadows the built-in default.
 */
record DemoCli(Mode mode, Map<String, String> overrides, boolean interactive) {

    /** What the invocation asked for. */
    enum Mode {
        /** Run the demo, reporting flag changes as they arrive. */
        RUN,
        /** Print diagnostics and exit. */
        DOCTOR,
        /** Print usage and exit. */
        HELP,
        /** Usage error; a message has already been printed. */
        INVALID
    }

    /** Option name to the setting (environment variable) name it overrides. */
    private static final Map<String, String> OPTIONS = Map.of(
            "--mobile-key", "LD_MOBILE_KEY",
            "--client-side-id", "LD_CLIENT_SIDE_ID",
            "--flags", "LD_FLAG_KEYS",
            "--context-kind", "LD_CONTEXT_KIND",
            "--context-key", "LD_CONTEXT_KEY",
            "--env", "LD_ENV",
            "--auto-env", "LD_AUTO_ENV_ATTRIBUTES",
            "--interval", "DEMO_INTERVAL_SECONDS");

    static DemoCli parse(String[] args) {
        Map<String, String> overrides = new LinkedHashMap<>();
        Mode mode = Mode.RUN;
        boolean interactive = true;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--help") || arg.equals("-h")) {
                return new DemoCli(Mode.HELP, Map.of(), false);
            }
            if (arg.equals("doctor")) {
                mode = Mode.DOCTOR;
                continue;
            }
            if (arg.equals("run")) {
                continue;
            }
            if (arg.equals("--no-interactive")) {
                interactive = false;
                continue;
            }
            if (arg.equals("--ticks")) {
                overrides.put("DEMO_TICKS", "true");
                continue;
            }

            // Accept both --option=value and --option value.
            String name = arg;
            String value = null;
            int equals = arg.indexOf('=');
            if (equals > 0) {
                name = arg.substring(0, equals);
                value = arg.substring(equals + 1);
            }

            String setting = OPTIONS.get(name);
            if (setting == null) {
                System.out.printf("Unknown option: %s%n%n", arg);
                printUsage();
                return new DemoCli(Mode.INVALID, Map.of(), false);
            }
            if (value == null) {
                // A value that looks like another option almost always means the
                // intended value went missing, so say so rather than silently
                // swallowing the next option.
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    System.out.printf("Option %s needs a value%n%n", name);
                    printUsage();
                    return new DemoCli(Mode.INVALID, Map.of(), false);
                }
                value = args[++i];
            }
            overrides.put(setting, value);
        }
        return new DemoCli(mode, Map.copyOf(overrides), interactive);
    }

    static void printUsage() {
        System.out.println("""
                Usage: launchdarkly-java-client-demo-app [run|doctor] [options]

                Commands
                  run                    Print the watched flags once, then report each
                                         change as it arrives. The default.
                  doctor                 Print the connection state and each flag's value,
                                         type and evaluation reason, then exit.

                Options                                          (environment variable)
                  --mobile-key KEY       Mobile key.              (LD_MOBILE_KEY)
                  --client-side-id ID    Client-side ID. Use one  (LD_CLIENT_SIDE_ID)
                                         credential, not both. Omit both for offline mode.
                  --flags KEY,KEY        Flags to watch.          (LD_FLAG_KEYS)
                  --context-kind KIND    Context kind.            (LD_CONTEXT_KIND)
                  --context-key KEY      Context key.             (LD_CONTEXT_KEY)
                  --env NAME             Context 'env' attribute. (LD_ENV)
                  --auto-env true|false  Add the ld_application   (LD_AUTO_ENV_ATTRIBUTES)
                                         and ld_device contexts. Default: true.
                  --ticks                Also reprint the flags   (DEMO_TICKS)
                                         every interval. Off by default; use l instead.
                  --interval SECONDS     Seconds between ticks,   (DEMO_INTERVAL_SECONDS)
                                         and between sample changes offline. Default: 5.
                  --no-interactive       Do not read commands from the terminal.
                  -h, --help             Print this help.

                While running
                  l          print the watched flags now
                  a          print every flag
                  i KEY      identify as another context (same kind and env)
                  t EVENT    track a custom event
                  o          go offline, or back online
                  f          flush analytics events
                  s          print connection status
                  h          list these commands
                  q          quit

                Examples
                  # Offline, changing the sample flags every second
                  launchdarkly-java-client-demo-app --interval 1

                  # Live with a mobile key, watching two flags
                  launchdarkly-java-client-demo-app --mobile-key $LD_MOBILE_KEY --flags new-checkout,banner-text

                  # Diagnose a flag that is not behaving
                  launchdarkly-java-client-demo-app doctor --mobile-key $LD_MOBILE_KEY

                Run via Gradle with:  ./gradlew run --args="doctor"
                """);
    }
}
