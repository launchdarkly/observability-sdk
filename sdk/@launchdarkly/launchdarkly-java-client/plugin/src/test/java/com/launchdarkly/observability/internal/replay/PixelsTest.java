package com.launchdarkly.observability.internal.replay;

import org.junit.Test;

import java.awt.Rectangle;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;

/** Masks on a frame's pixels. */
public class PixelsTest {
  @Test
  public void aMaskIsCutToTheFrame() {
    int[] pixels = new int[4];
    int mask = CaptureLoop.MASK_COLOR.getRGB();
    Pixels.mask(pixels, 2, 2, Arrays.asList(new Rectangle(-5, 0, 3, 2), new Rectangle(1, -1, 5, 2),
        new Rectangle(0, 5, 2, 2)));
    assertArrayEquals(new int[] {0, mask, 0, 0}, pixels);
  }
}
