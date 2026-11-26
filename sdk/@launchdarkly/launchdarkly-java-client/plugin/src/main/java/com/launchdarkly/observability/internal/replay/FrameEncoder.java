package com.launchdarkly.observability.internal.replay;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Turns captured frames into what is sent: only the part of the screen that changed, as a JPEG over
 * what the player already shows. The Android plugin's {@code TileDiffManager} and
 * {@code ExportDiffManager} together:
 * <ul>
 *   <li>a frame identical to the last is not sent;</li>
 *   <li>otherwise the bounding box of the changed tiles is sent as one image over the last ones;</li>
 *   <li>every {@code layers}th frame, a change covering the whole screen, or a new size is a
 *   keyframe: the whole frame, replacing every image before it;</li>
 *   <li>with backtracking, a frame identical to one still shown under later overlays, such as a
 *   dialog closing, is sent as the removal of those overlays, with no image at all.</li>
 * </ul>
 * That is Android's {@code OverlayTiles(layers, backtracking)}. With {@code screenImage}, Android's
 * {@code ScreenImage}, every frame that changed is a keyframe.
 * Runs on the capture thread, one frame at a time.
 */
final class FrameEncoder {
  /** Overlays between keyframes by default, as on Android. */
  static final int KEYFRAME_INTERVAL = 15;

  private final float quality;
  private final boolean screenImage;
  private final int layers;
  private final boolean backtracking;
  private final ImageWriter writer;

  private ImageSignature previous;
  private int incremental;
  private int keyFrameId;
  // The images the player shows, oldest first, and where each frame's image is in that stack.
  private final List<ExportFrame.Signature> shown = new ArrayList<>();
  private final Map<ExportFrame.Signature, Integer> shownAt = new HashMap<>();

  /** Android's default: overlay tiles, 15 layers, with backtracking. @param quality JPEG quality, from 0 to 1 */
  FrameEncoder(float quality) {
    this(quality, false, KEYFRAME_INTERVAL, true);
  }

  /**
   * @param quality JPEG quality, from 0 to 1
   * @param screenImage whether every frame is a keyframe, with no overlays
   * @param layers overlays between keyframes; 0 or less makes every frame a keyframe
   * @param backtracking whether a return to a frame still shown removes the overlays above it
   */
  FrameEncoder(float quality, boolean screenImage, int layers, boolean backtracking) {
    this.quality = quality;
    this.screenImage = screenImage;
    this.layers = layers;
    this.backtracking = backtracking;
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    this.writer = writers.next(); // every JRE has a JPEG writer
  }

  /** The frame to send, or null if nothing changed. {@code image} is TYPE_INT_RGB. */
  ExportFrame encode(BufferedImage image, long timestamp, String sessionId) {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    ImageSignature signature = ImageSignature.of(pixels, width, height);
    Rectangle changed = signature.diff(previous);
    if (changed == null) {
      return null;
    }
    previous = signature;

    boolean keyframe;
    if (screenImage || layers <= 0) {
      keyframe = true;
    } else {
      incremental = (incremental + 1) % layers;
      boolean wholeScreen = changed.width >= width && changed.height >= height;
      keyframe = incremental == 0 || wholeScreen;
      if (wholeScreen) {
        incremental = 0;
      }
    }

    List<ExportFrame.Signature> removes = Collections.emptyList();
    if (keyframe) {
      removes = new ArrayList<>(shown);
      shown.clear();
      shownAt.clear();
      keyFrameId++;
    }

    // A screen image is never removed alone, so it carries no signature, as on Android.
    ExportFrame.Signature digest = screenImage ? null : signature.digest();
    List<ExportFrame.AddImage> adds = new ArrayList<>(1);
    Integer earlier = backtracking && digest != null ? shownAt.get(digest) : null;
    if (earlier != null) {
      // Back to a frame the player still shows: take away what was drawn over it.
      List<ExportFrame.Signature> above = shown.subList(earlier + 1, shown.size());
      removes = new ArrayList<>(above);
      for (ExportFrame.Signature gone : above) {
        shownAt.remove(gone);
      }
      above.clear();
    } else {
      Rectangle rect = keyframe ? new Rectangle(0, 0, width, height)
          : changed.intersection(new Rectangle(0, 0, width, height));
      BufferedImage part = keyframe ? image : image.getSubimage(rect.x, rect.y, rect.width, rect.height);
      adds.add(new ExportFrame.AddImage(jpeg(part), rect.x, rect.y, rect.width, rect.height, digest));
      if (digest != null) {
        shown.add(digest);
        shownAt.put(digest, shown.size() - 1);
      }
    }
    // Never empty: a frame back to one still shown has overlays to remove, since it differs from the last.
    return new ExportFrame(keyFrameId, adds, removes, width, height, timestamp, keyframe, sessionId);
  }

  /** Starts afresh, so the next frame is a keyframe: after a pause, or for a new session. */
  void reset() {
    previous = null;
    incremental = 0;
  }

  /** The image as a JPEG, base64 encoded into ASCII bytes. */
  byte[] jpeg(BufferedImage image) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(1024, image.getWidth() * image.getHeight() / 8));
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(quality);
    try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
      writer.setOutput(out);
      writer.write(null, new IIOImage(image, null, null), param);
    } catch (IOException e) {
      throw new IllegalStateException(e); // writing to memory does not fail
    } finally {
      writer.reset();
    }
    return Base64.getEncoder().encode(bytes.toByteArray());
  }

  void dispose() {
    writer.dispose();
  }
}
