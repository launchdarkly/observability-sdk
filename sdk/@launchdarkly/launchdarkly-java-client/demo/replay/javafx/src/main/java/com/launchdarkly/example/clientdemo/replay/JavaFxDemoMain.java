package com.launchdarkly.example.clientdemo.replay;

/**
 * Starts the JavaFX demo. JavaFX refuses to start from the classpath when the main class is the
 * application itself, so the main class is this one.
 */
public final class JavaFxDemoMain {

    private JavaFxDemoMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) {
        JavaFxDemo.replay = ReplayDemo.start("launchdarkly-java-client-demo-replay-javafx");
        javafx.application.Application.launch(JavaFxDemo.class, args);
        JavaFxDemo.replay.stop();
    }
}
