package com.launchdarkly.observability;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.internal.ObservabilityRuntime;
import com.launchdarkly.observability.internal.ObservabilityService;
import com.launchdarkly.observability.internal.replay.MaskRules;
import com.launchdarkly.observability.internal.replay.ReplayService;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Controls session replay, and marks what it masks. The {@link SessionReplay} plugin installs
 * replay when the {@code LDClient} starts; {@link #init} installs it without one.
 * <p>
 * Every method is safe to call from any thread, at any time. Before replay is installed,
 * {@link #setEnabled}, {@link #start()} and {@link #stop()} are remembered and applied when it is,
 * as on Android; the marks apply from the next frame on.
 *
 * <pre><code>
 *     // Mask a panel and everything in it, whatever the privacy profile says.
 *     LDReplay.mask(accountPanel);
 *     // Stop recording while a sensitive flow is on screen, then start again.
 *     LDReplay.stop();
 *     LDReplay.start();
 * </code></pre>
 */
public final class LDReplay {
  /**
   * A Swing client property ({@code putClientProperty}), JavaFX node property
   * ({@code getProperties().put}), SWT widget data ({@code setData}) or Apache Pivot user data:
   * {@code Boolean.TRUE} masks the component or node and everything in it, {@code Boolean.FALSE}
   * unmasks them.
   */
  public static final String MASK_PROPERTY = MaskRules.PROPERTY;
  /** A JavaFX style class that masks the node and everything in it. */
  public static final String MASK_STYLE_CLASS = "ld-mask";
  /** A JavaFX style class that unmasks the node and everything in it, unless something masks them. */
  public static final String UNMASK_STYLE_CLASS = "ld-unmask";

  /** Builds the replay service. Replaced in tests. */
  static BiFunction<ObservabilityService, ReplayOptions, ReplayService> factory = ReplayService::create;

  private static final Object LOCK = new Object();
  // Guarded by LOCK.
  private static ReplayService live;
  private static Boolean pendingEnabled;
  private static boolean pendingStart;
  private static boolean pendingIgnoreSampling;

  private LDReplay() {}

  /**
   * Installs session replay without an {@code LDClient}, after {@link LDObserve#init} has started
   * observability. With a client, add the {@link SessionReplay} plugin instead. Does nothing when
   * replay is already installed.
   *
   * @param options the options
   */
  public static void init(ReplayOptions options) {
    Objects.requireNonNull(options, "options");
    ObservabilityService service = ObservabilityRuntime.current();
    if (service == null) {
      logger().warn("Session replay is not installed: LaunchDarkly observability is not running. Call"
          + " LDObserve.init first.");
      return;
    }
    install(service, options);
  }

  /** Installs replay for the observability service of this credential. For {@link SessionReplay}. */
  static void install(String credential, ReplayOptions options) {
    ObservabilityService service = ObservabilityRuntime.currentFor(credential);
    if (service == null) {
      logger().warn("Session replay is not installed: LaunchDarkly observability is not running for this"
          + " client. Add the Observability plugin to the client's plugins.");
      return;
    }
    install(service, options);
  }

  private static void install(ObservabilityService service, ReplayOptions options) {
    ReplayService replay;
    boolean enable;
    boolean ignoreSampling;
    synchronized (LOCK) {
      if (live != null) {
        service.getLogger().warn("Session replay is already installed; this one is ignored");
        return;
      }
      try {
        replay = factory.apply(service, options);
      } catch (RuntimeException | LinkageError e) {
        // Such as an OkHttp older than replay needs: it must not stop the application starting.
        service.getLogger().error("Session replay could not start, so it records nothing: {}", e.toString());
        return;
      }
      enable = pendingStart || (pendingEnabled != null ? pendingEnabled : options.isEnabled());
      ignoreSampling = pendingStart && pendingIgnoreSampling;
      pendingEnabled = null;
      pendingStart = false;
      pendingIgnoreSampling = false;
      live = replay;
    }
    replay.setOnStopped(() -> {
      synchronized (LOCK) {
        if (live == replay) {
          live = null;
        }
      }
    });
    service.addListener(replay);
    replay.begin(enable, ignoreSampling);
  }

  private static ReplayService live() {
    synchronized (LOCK) {
      return live;
    }
  }

  private static LDLogger logger() {
    ObservabilityOptions defaults = ObservabilityOptions.builder().build();
    return LDLogger.withAdapter(defaults.getLogAdapter(), defaults.getLoggerName());
  }

  /**
   * Whether replay is enabled. A session sampling left out is enabled but not recording. Before
   * replay is installed, what it will be.
   */
  public static boolean isEnabled() {
    synchronized (LOCK) {
      if (live != null) {
        return live.isEnabled();
      }
      return pendingStart || (pendingEnabled != null && pendingEnabled);
    }
  }

  /**
   * Enables or disables replay. Enabling starts recording unless sampling leaves the session out;
   * sampling decides once each time replay is enabled.
   *
   * @param enabled whether to enable it
   */
  public static void setEnabled(boolean enabled) {
    ReplayService replay;
    synchronized (LOCK) {
      replay = live;
      if (replay == null) {
        pendingEnabled = enabled;
        pendingStart = false;
        pendingIgnoreSampling = false;
        return;
      }
    }
    replay.setEnabled(enabled);
  }

  /**
   * Enables replay and starts recording, and says what happened. Unlike {@code setEnabled(true)},
   * it tries even when replay is already enabled.
   *
   * @return the outcome; {@link SessionReplayStartResult#UNAVAILABLE} before replay is installed,
   *     when the start is remembered
   */
  public static SessionReplayStartResult start() {
    return start(false);
  }

  /**
   * Enables replay and starts recording, and says what happened.
   *
   * @param ignoreSampling records even when sampling has left this session out; for debugging
   * @return the outcome; {@link SessionReplayStartResult#UNAVAILABLE} before replay is installed,
   *     when the start is remembered
   */
  public static SessionReplayStartResult start(boolean ignoreSampling) {
    ReplayService replay;
    synchronized (LOCK) {
      replay = live;
      if (replay == null) {
        pendingStart = true;
        pendingIgnoreSampling |= ignoreSampling;
        pendingEnabled = null;
        return SessionReplayStartResult.UNAVAILABLE;
      }
    }
    return replay.start(ignoreSampling);
  }

  /** Stops recording and disables replay. */
  public static void stop() {
    setEnabled(false);
  }

  /** Sends what replay has queued now, without waiting. */
  public static void flush() {
    ReplayService replay = live();
    if (replay != null) {
      replay.flush();
    }
  }

  /**
   * Masks a Swing, AWT, SWT or Apache Pivot component, or a JavaFX node, and everything in it,
   * whatever the privacy profile says. The mark does not keep the component from being collected.
   *
   * @param componentOrNode the component or node
   */
  public static void mask(Object componentOrNode) {
    MaskRules.mark(Objects.requireNonNull(componentOrNode, "componentOrNode"), Boolean.TRUE);
  }

  /**
   * Unmasks a component or node and everything in it, unless something in it or around it is
   * marked to mask. A password field stays masked.
   *
   * @param componentOrNode the component or node
   */
  public static void unmask(Object componentOrNode) {
    MaskRules.mark(Objects.requireNonNull(componentOrNode, "componentOrNode"), Boolean.FALSE);
  }

  /**
   * Removes a mark made by {@link #mask} or {@link #unmask}, so the privacy profile decides again.
   *
   * @param componentOrNode the component or node
   */
  public static void clearMask(Object componentOrNode) {
    MaskRules.mark(Objects.requireNonNull(componentOrNode, "componentOrNode"), null);
  }

  /** Stops replay and forgets anything remembered. For tests. */
  static void resetForTest() {
    ReplayService replay;
    synchronized (LOCK) {
      replay = live;
      live = null;
      pendingEnabled = null;
      pendingStart = false;
      pendingIgnoreSampling = false;
      factory = ReplayService::create;
    }
    if (replay != null) {
      replay.shutdown();
    }
  }
}
