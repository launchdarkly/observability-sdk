package com.launchdarkly.observability.internal.replay;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One captured frame, ready to send: the image tiles to add over what the player already shows,
 * and the earlier tiles to take away. A keyframe replaces everything shown before it.
 */
final class ExportFrame {
  /** The hash of an image's pixels, which tells two images apart. */
  static final class Signature {
    final long low;
    final long high;

    Signature(long low, long high) {
      this.low = low;
      this.high = high;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Signature)) {
        return false;
      }
      Signature that = (Signature) other;
      return low == that.low && high == that.high;
    }

    @Override
    public int hashCode() {
      return Objects.hash(low, high);
    }
  }

  /** A JPEG tile, placed at its position on the screen. */
  static final class AddImage {
    final String base64;
    final int left;
    final int top;
    final int width;
    final int height;
    /** The tile's signature, for removing it later; null when it is never removed alone. */
    final Signature signature;

    AddImage(String base64, int left, int top, int width, int height, Signature signature) {
      this.base64 = base64;
      this.left = left;
      this.top = top;
      this.width = width;
      this.height = height;
      this.signature = signature;
    }
  }

  final int keyFrameId;
  final List<AddImage> addImages;
  final List<Signature> removeImages;
  final int width;
  final int height;
  final long timestamp;
  final boolean keyframe;
  final String sessionId;

  ExportFrame(int keyFrameId, List<AddImage> addImages, List<Signature> removeImages, int width, int height,
      long timestamp, boolean keyframe, String sessionId) {
    this.keyFrameId = keyFrameId;
    this.addImages = Collections.unmodifiableList(addImages);
    this.removeImages = Collections.unmodifiableList(removeImages);
    this.width = width;
    this.height = height;
    this.timestamp = timestamp;
    this.keyframe = keyframe;
    this.sessionId = sessionId;
  }

  /** The queue cost of the frame: the size of its images. */
  int cost() {
    int cost = 0;
    for (AddImage image : addImages) {
      cost += image.base64.length();
    }
    return cost;
  }
}
