package com.launchdarkly.observability.internal;

/** The instrumentation of one UI toolkit: AWT and Swing, or JavaFX. */
interface ToolkitInstrumentation {
  /** Where an instrumentation reports what it sees. */
  interface Signals {
    void foreground();

    void background();

    /**
     * The application had no active window when the plugin started, and still has none after the
     * startup grace period. No span, as on Android, but the session's background timeout applies.
     */
    void startedInBackground();

    void screenShown(String name, String screenClass, String screenId);

    void click(Click click);
  }

  /** Attaches now if the toolkit is in use, or as soon as it is. Never starts the toolkit. */
  void start();

  /** Detaches and stops checking. */
  void stop();

  /**
   * Whether one of the toolkit's windows is active now. False until attached. Must not block or take
   * a lock another toolkit's instrumentation might hold.
   */
  boolean hasActiveWindow();
}
