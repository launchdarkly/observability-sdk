package com.launchdarkly.observability.internal.replay;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/** Where frames come from: one UI toolkit's active window. */
interface CaptureSource {
  /** A painted frame, and what in it must be masked before it leaves the process. */
  final class RawFrame {
    /** The frame, TYPE_INT_RGB. */
    final BufferedImage image;
    /**
     * Rectangles still to cover, in the image's pixels. The Swing and JavaFX captures mask as they
     * paint, so theirs is empty.
     */
    final List<Rectangle> masks;
    final long timestamp;
    /** Where the frame's top left corner is on the screen, in the toolkit's logical pixels. */
    final double originX;
    final double originY;
    /** Frame pixels per logical pixel. */
    final double scale;

    RawFrame(BufferedImage image, List<Rectangle> masks, long timestamp) {
      this(image, masks, timestamp, 0, 0, 1);
    }

    RawFrame(BufferedImage image, List<Rectangle> masks, long timestamp, double originX, double originY,
        double scale) {
      this.image = image;
      this.masks = Collections.unmodifiableList(masks);
      this.timestamp = timestamp;
      this.originX = originX;
      this.originY = originY;
      this.scale = scale;
    }
  }

  /**
   * Paints the toolkit's active window, if it has one, on the toolkit's UI thread, waiting up to
   * {@code timeoutMillis} for that thread. Never starts the toolkit.
   *
   * @return the frame, or null if the toolkit has no active window, its thread is busy past the
   *     timeout, or painting failed
   */
  RawFrame capture(long timeoutMillis);

  /** Stops whatever the source attached to the toolkit. */
  default void stop() {}
}
