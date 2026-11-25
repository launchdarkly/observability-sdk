package com.launchdarkly.example.clientdemo;

import com.launchdarkly.example.clientdemo.dashboard.DashboardApp;
import java.util.Arrays;

/**
 * Entry point for the runnable jar, which holds both demos.
 *
 * <pre>
 *   java -jar launchdarkly-java-client-demo-app.jar            the dashboard
 *   java -jar launchdarkly-java-client-demo-app.jar console    the console demo
 *   java -jar launchdarkly-java-client-demo-app.jar doctor     console diagnostics
 * </pre>
 *
 * <p>Anything after {@code console} goes to the console demo, so
 * {@code console --help} lists its options.
 */
public final class Launcher {

    private Launcher() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) throws Exception {
        String first = args.length == 0 ? "dashboard" : args[0];
        switch (first) {
            case "dashboard" -> DashboardApp.main(new String[0]);
            case "console" -> DemoApp.main(Arrays.copyOfRange(args, 1, args.length));
            // The console demo already treats these as its own commands.
            case "doctor", "--help", "-h" -> DemoApp.main(args);
            default -> {
                System.out.println("Unknown command '" + first + "'.");
                System.out.println("Use: dashboard (the default), console [options], or doctor.");
                System.exit(2);
            }
        }
    }
}
