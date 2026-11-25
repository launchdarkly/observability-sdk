package com.launchdarkly.observability.internal.replay;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.List;

/**
 * A frame as an array of RGB pixels, as the JavaFX and SWT captures copy them on their toolkit's
 * thread. Free of any toolkit's classes.
 */
final class Pixels {
  private Pixels() {}

  /** Covers the rectangles, cut to the frame, in the mask color. */
  static void mask(int[] pixels, int width, int height, List<Rectangle> masks) {
    int color = CaptureLoop.MASK_COLOR.getRGB();
    for (Rectangle r : masks) {
      int left = Math.max(0, r.x);
      int right = Math.min(width, r.x + r.width);
      for (int row = Math.max(0, r.y); row < Math.min(height, r.y + r.height); row++) {
        if (left < right) {
          Arrays.fill(pixels, row * width + left, row * width + right, color);
        }
      }
    }
  }

  /** The pixels as the frame's image, at this scale. Off the toolkit's thread. */
  static BufferedImage image(int[] pixels, int width, int height, double scale) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    System.arraycopy(pixels, 0, target, 0, target.length);
    if (scale == 1) {
      return image;
    }
    int w = Math.max(1, (int) Math.round(width * scale));
    int h = Math.max(1, (int) Math.round(height * scale));
    BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = scaled.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(image, 0, 0, w, h, null);
    } finally {
      g.dispose();
    }
    return scaled;
  }
}
