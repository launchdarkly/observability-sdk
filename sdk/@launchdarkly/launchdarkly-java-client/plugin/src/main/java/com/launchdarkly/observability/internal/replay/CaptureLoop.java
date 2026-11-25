package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Takes a frame every interval and queues what changed. A frame comes from the first toolkit with
 * an active window, is masked, and only then encoded: unmasked pixels never leave this class.
 * <p>
 * Capture pauses while the application is in the background, and skips frames while the queue is
 * full rather than encode what would be dropped. A new session starts with a keyframe, so its
 * replay never builds on images from the session before.
 */
final class CaptureLoop {
  /** The color masks are drawn in, as on Android. */
  static final Color MASK_COLOR = new Color(0x858585);

  private final List<CaptureSource> sources;
  private final FrameEncoder encoder;
  private final ReplayQueue queue;
  private final Supplier<String> sessions;
  private final long intervalMillis;
  private final LDLogger logger;
  private final ScheduledExecutorService executor;

  /** Told of each frame taken, before it is masked; on the capture thread. */
  volatile Consumer<CaptureSource.RawFrame> onFrame = frame -> { };
  /** Run at every tick, paused or not; on the capture thread. */
  volatile Runnable onTick = () -> { };
  private volatile boolean paused;
  // Touched only on the capture thread.
  private String lastSession;

  CaptureLoop(List<CaptureSource> sources, FrameEncoder encoder, ReplayQueue queue, Supplier<String> sessions,
      long intervalMillis, LDLogger logger) {
    this(sources, encoder, queue, sessions, intervalMillis, logger, Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "LaunchDarkly-session-replay-capture");
      thread.setDaemon(true);
      return thread;
    }));
  }

  CaptureLoop(List<CaptureSource> sources, FrameEncoder encoder, ReplayQueue queue, Supplier<String> sessions,
      long intervalMillis, LDLogger logger, ScheduledExecutorService executor) {
    this.sources = sources;
    this.encoder = encoder;
    this.queue = queue;
    this.sessions = sessions;
    this.intervalMillis = intervalMillis;
    this.logger = logger;
    this.executor = executor;
  }

  void start() {
    executor.scheduleWithFixedDelay(this::tick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
  }

  void pause() {
    paused = true;
  }

  void resume() {
    paused = false;
  }

  boolean isPaused() {
    return paused;
  }

  /** Stops capturing, waiting briefly for a frame in progress. */
  void stop() {
    executor.shutdown();
    try {
      executor.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    executor.shutdownNow();
    for (CaptureSource source : sources) {
      source.stop();
    }
    encoder.dispose();
  }

  /** Takes, masks, encodes and queues one frame. On the capture thread. */
  void tick() {
    try {
      onTick.run();
    } catch (RuntimeException e) {
      logger.debug("Session replay tick failed: {}", e.toString());
    }
    if (paused || queue.isFull()) {
      return;
    }
    try {
      CaptureSource.RawFrame raw = null;
      for (CaptureSource source : sources) {
        raw = source.capture(intervalMillis);
        if (raw != null) {
          break;
        }
      }
      if (raw == null) {
        return;
      }
      onFrame.accept(raw);
      mask(raw);
      // Asked only with a frame in hand: asking keeps a session alive, as any signal does.
      String session = sessions.get();
      if (!session.equals(lastSession)) {
        encoder.reset();
        lastSession = session;
      }
      ExportFrame frame = encoder.encode(raw.image, raw.timestamp, session);
      if (frame != null) {
        queue.offer(new ReplayItem.Frame(frame));
      }
    } catch (RuntimeException e) {
      logger.debug("Session replay skipped a frame: {}", e.toString());
    } catch (OutOfMemoryError e) {
      // A frame is a few megabytes; when there is not that much to spare, replay steps aside.
      paused = true;
      logger.warn("Session replay paused: not enough memory to capture the screen");
    }
  }

  static void mask(CaptureSource.RawFrame raw) {
    mask(raw.image, raw.masks);
  }

  /** Covers the rectangles of the image in the mask color. */
  static void mask(java.awt.image.BufferedImage image, List<Rectangle> masks) {
    if (masks.isEmpty()) {
      return;
    }
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(MASK_COLOR);
      for (Rectangle r : masks) {
        g.fillRect(r.x, r.y, r.width, r.height);
      }
    } finally {
      g.dispose();
    }
  }
}
