package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Signatures, the frame encoder, the capture loop, and painting Swing windows. */
public class CaptureTest {
  private final List<Window> windows = new ArrayList<>();

  @After
  public void tearDown() throws Exception {
    SwingUtilities.invokeAndWait(() -> windows.forEach(Window::dispose));
  }

  static BufferedImage image(int width, int height, int rgb) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(rgb));
    g.fillRect(0, 0, width, height);
    g.dispose();
    return image;
  }

  static BufferedImage paint(BufferedImage image, int x, int y, int w, int h, int rgb) {
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(rgb));
    g.fillRect(x, y, w, h);
    g.dispose();
    return image;
  }

  static BufferedImage copy(BufferedImage image) {
    BufferedImage c = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    c.getGraphics().drawImage(image, 0, 0, null);
    return c;
  }

  static int[] pixels(BufferedImage image) {
    return ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
  }

  // -- signatures --

  @Test
  public void aSignatureFindsTheChangedTiles() {
    BufferedImage a = image(100, 70, 0xffffff);
    BufferedImage b = paint(copy(a), 40, 40, 1, 1, 0x000000);
    ImageSignature sa = ImageSignature.of(pixels(a), 100, 70);
    ImageSignature sb = ImageSignature.of(pixels(b), 100, 70);
    assertEquals("ceil(100/32) by ceil(70/32)", 4, sa.columns);
    assertEquals(3, sa.rows);
    assertNull(sa.diff(ImageSignature.of(pixels(copy(a)), 100, 70)));
    assertEquals(new Rectangle(32, 32, 32, 32), sb.diff(sa));
    BufferedImage c = paint(copy(b), 99, 69, 1, 1, 0x000000);
    assertEquals("to the edge tile", new Rectangle(32, 32, 96, 64),
        ImageSignature.of(pixels(c), 100, 70).diff(sa));
    assertEquals(new Rectangle(0, 0, 128, 96), sa.diff(null));
    assertEquals(new Rectangle(0, 0, 128, 96), sa.diff(ImageSignature.of(pixels(image(90, 70, 0)), 90, 70)));
    assertEquals(new Rectangle(0, 0, 128, 96), sa.diff(ImageSignature.of(pixels(image(100, 50, 0)), 100, 50)));
  }

  @Test
  public void aDigestNamesTheImage() {
    BufferedImage a = image(64, 64, 0x123456);
    assertEquals(ImageSignature.of(pixels(a), 64, 64).digest(), ImageSignature.of(pixels(copy(a)), 64, 64).digest());
    assertNotEquals(ImageSignature.of(pixels(a), 64, 64).digest(),
        ImageSignature.of(pixels(paint(copy(a), 1, 1, 1, 1, 0)), 64, 64).digest());
    assertNotEquals(ImageSignature.mix(1), ImageSignature.mix(2));
  }

  // -- the encoder --

  @Test
  public void theFirstFrameIsAKeyframeAndAnUnchangedOneIsNotSent() throws Exception {
    FrameEncoder encoder = new FrameEncoder(0.3f);
    BufferedImage a = image(200, 100, 0xeeeeee);
    ExportFrame first = encoder.encode(a, 5, "s");
    assertTrue(first.keyframe);
    assertEquals(1, first.keyFrameId);
    assertEquals(1, first.addImages.size());
    ExportFrame.AddImage whole = first.addImages.get(0);
    assertEquals(200, whole.width);
    assertEquals(100, whole.height);
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(whole.base64)));
    assertEquals(200, decoded.getWidth());
    assertEquals(5, first.timestamp);
    assertEquals("s", first.sessionId);
    assertEquals(200, first.width);
    assertNull(encoder.encode(copy(a), 6, "s"));
    encoder.dispose();
  }

  @Test
  public void aSmallChangeIsSentAsTheTilesAroundIt() {
    FrameEncoder encoder = new FrameEncoder(0.3f);
    BufferedImage a = image(200, 100, 0xeeeeee);
    encoder.encode(a, 1, "s");
    BufferedImage changed = paint(copy(a), 70, 10, 5, 5, 0xff0000);
    ExportFrame change = encoder.encode(changed, 2, "s");
    assertFalse(change.keyframe);
    assertEquals(1, change.keyFrameId);
    ExportFrame.AddImage tile = change.addImages.get(0);
    assertEquals(64, tile.left);
    assertEquals(0, tile.top);
    assertEquals(32, tile.width);
    assertEquals(32, tile.height);
    assertThat(change.removeImages, empty());
    ExportFrame row = encoder.encode(paint(copy(changed), 0, 40, 200, 2, 0x0000ff), 3, "s");
    assertFalse("the full width, but not the full height", row.keyframe);
    changed = paint(copy(changed), 0, 40, 200, 2, 0x0000ff);
    // At the edge, the tiles are cut to the frame.
    ExportFrame edge = encoder.encode(paint(copy(changed), 196, 96, 4, 4, 0x00ff00), 4, "s");
    assertEquals(8, edge.addImages.get(0).width);
    assertEquals(4, edge.addImages.get(0).height);
  }

  @Test
  public void everyFifteenthFrameOrAWholeScreenChangeIsAKeyframe() {
    FrameEncoder encoder = new FrameEncoder(0.3f);
    BufferedImage current = image(64, 64, 0xffffff);
    encoder.encode(current, 0, "s");
    List<Boolean> keyframes = new ArrayList<>();
    for (int i = 1; i <= FrameEncoder.KEYFRAME_INTERVAL; i++) {
      current = paint(copy(current), 0, 0, 1, 1, i * 1000);
      keyframes.add(encoder.encode(current, i, "s").keyframe);
    }
    assertTrue(keyframes.get(FrameEncoder.KEYFRAME_INTERVAL - 1));
    assertEquals(1, keyframes.stream().filter(k -> k).count());
    ExportFrame whole = encoder.encode(image(64, 64, 0x000000), 99, "s");
    assertTrue(whole.keyframe);
    assertEquals("a keyframe removes the images before it", 1, whole.removeImages.size());
    ExportFrame resized = encoder.encode(image(80, 64, 0x000000), 100, "s");
    assertTrue(resized.keyframe);
    assertEquals(80, resized.width);
  }

  @Test
  public void goingBackToAShownFrameRemovesWhatCoveredIt() {
    FrameEncoder encoder = new FrameEncoder(0.3f);
    BufferedImage screen = image(128, 128, 0xffffff);
    encoder.encode(screen, 0, "s");
    BufferedImage withDialog = paint(copy(screen), 40, 40, 40, 40, 0x0000ff);
    ExportFrame shown = encoder.encode(withDialog, 1, "s");
    BufferedImage withMenu = paint(copy(withDialog), 0, 0, 10, 10, 0x00ff00);
    ExportFrame menu = encoder.encode(withMenu, 2, "s");
    ExportFrame back = encoder.encode(copy(screen), 3, "s");
    assertThat("no image, just the overlays removed", back.addImages, empty());
    assertThat(back.removeImages, contains(shown.addImages.get(0).signature, menu.addImages.get(0).signature));
    ExportFrame again = encoder.encode(copy(withDialog), 4, "s");
    assertEquals("not shown any more, so drawn again", 1, again.addImages.size());
  }

  @Test
  public void screenImageSendsEveryChangeWholeAndUnsigned() {
    FrameEncoder encoder = new FrameEncoder(0.3f, true, 0, false);
    BufferedImage screen = image(64, 64, 0xffffff);
    ExportFrame first = encoder.encode(screen, 0, "s");
    BufferedImage dot = paint(copy(screen), 0, 0, 2, 2, 0x0000ff);
    ExportFrame second = encoder.encode(dot, 1, "s");
    assertTrue(first.keyframe);
    assertTrue("a small change is still the whole frame", second.keyframe);
    assertEquals(64, second.addImages.get(0).width);
    assertNull("never removed alone, as on Android", second.addImages.get(0).signature);
    assertThat("nothing tracked, so nothing to remove", second.removeImages, empty());
    ExportFrame back = encoder.encode(copy(screen), 2, "s");
    assertEquals("no backtracking: drawn again", 1, back.addImages.size());
    assertNull("an unchanged frame is still not sent", encoder.encode(copy(screen), 3, "s"));
  }

  @Test
  public void overlayTilesWithNoLayersSendsEveryFrameWhole() {
    FrameEncoder encoder = new FrameEncoder(0.3f, false, 0, true);
    BufferedImage screen = image(64, 64, 0xffffff);
    encoder.encode(screen, 0, "s");
    ExportFrame next = encoder.encode(paint(copy(screen), 0, 0, 2, 2, 0x0000ff), 1, "s");
    assertTrue(next.keyframe);
    assertEquals("signed, so the next keyframe removes it", 1, next.removeImages.size());
  }

  @Test
  public void withoutBacktrackingAReturnIsDrawnAgain() {
    FrameEncoder encoder = new FrameEncoder(0.3f, false, 15, false);
    BufferedImage screen = image(128, 128, 0xffffff);
    encoder.encode(screen, 0, "s");
    BufferedImage withDialog = paint(copy(screen), 40, 40, 40, 40, 0x0000ff);
    ExportFrame dialog = encoder.encode(withDialog, 1, "s");
    assertFalse(dialog.keyframe);
    ExportFrame back = encoder.encode(copy(screen), 2, "s");
    assertEquals("the closed dialog's area is sent again", 1, back.addImages.size());
    assertThat(back.removeImages, empty());
  }

  @Test
  public void aResetStartsWithAKeyframe() {
    FrameEncoder encoder = new FrameEncoder(0.3f);
    BufferedImage a = image(64, 64, 0xffffff);
    encoder.encode(a, 0, "s");
    encoder.reset();
    ExportFrame again = encoder.encode(copy(a), 1, "s2");
    assertTrue(again.keyframe);
    assertEquals(2, again.keyFrameId);
    assertEquals(1, again.removeImages.size());
  }

  // -- the loop --

  /** A source that hands out frames a test sets. */
  private static final class FakeSource implements CaptureSource {
    final AtomicReference<RawFrame> next = new AtomicReference<>();
    final AtomicInteger asked = new AtomicInteger();
    RuntimeException failure;
    Error error;

    @Override
    public RawFrame capture(long timeoutMillis) {
      asked.incrementAndGet();
      if (failure != null) {
        throw failure;
      }
      if (error != null) {
        throw error;
      }
      return next.get();
    }
  }

  @Test
  public void theLoopMasksEncodesAndQueues() {
    FakeSource none = new FakeSource();
    FakeSource swing = new FakeSource();
    ReplayQueue queue = new ReplayQueue();
    AtomicReference<String> session = new AtomicReference<>("s1");
    CaptureLoop loop = new CaptureLoop(Arrays.asList(none, swing), new FrameEncoder(1f), queue, session::get, 1000,
        LDLogger.none(), Executors.newSingleThreadScheduledExecutor());
    loop.tick();
    assertEquals("no window, no frame", 0, queue.size());

    BufferedImage image = image(64, 64, 0xff0000);
    swing.next.set(new CaptureSource.RawFrame(image, Collections.singletonList(new Rectangle(0, 0, 10, 10)), 7));
    loop.tick();
    assertEquals(1, queue.size());
    assertEquals(CaptureLoop.MASK_COLOR.getRGB(), image.getRGB(5, 5));
    assertEquals(0xffff0000, image.getRGB(20, 20));
    assertEquals("the first source was asked first", 2, none.asked.get());

    swing.next.set(new CaptureSource.RawFrame(image(64, 64, 0xff0000), Collections.singletonList(
        new Rectangle(0, 0, 10, 10)), 8));
    loop.tick();
    assertEquals("the same masked frame again: nothing to send", 1, queue.size());

    session.set("s2");
    swing.next.set(new CaptureSource.RawFrame(image(64, 64, 0xff0000), Collections.emptyList(), 9));
    loop.tick();
    ReplayItem.Frame newSession = (ReplayItem.Frame) queue.peek(Long.MAX_VALUE, 10).get(1);
    assertTrue("a new session starts with a keyframe", newSession.frame.keyframe);
    assertEquals("s2", newSession.sessionId);
    loop.stop();
  }

  @Test
  public void theLoopSkipsWhilePausedOrFull() {
    FakeSource source = new FakeSource();
    source.next.set(new CaptureSource.RawFrame(image(8, 8, 0), Collections.emptyList(), 1));
    ReplayQueue queue = new ReplayQueue(1);
    CaptureLoop loop = new CaptureLoop(Collections.singletonList(source), new FrameEncoder(0.3f), queue, () -> "s",
        1000, LDLogger.none(), Executors.newSingleThreadScheduledExecutor());
    loop.pause();
    assertTrue(loop.isPaused());
    loop.tick();
    assertEquals(0, source.asked.get());
    loop.resume();
    queue.offer(EventGenerator.navigate("x", 0, "s"));
    loop.tick();
    assertEquals("full", 0, source.asked.get());
    loop.stop();
  }

  @Test
  public void aFailedFrameIsSkippedAndRunningOutOfMemoryPauses() {
    FakeSource source = new FakeSource();
    CaptureLoop loop = new CaptureLoop(Collections.singletonList(source), new FrameEncoder(0.3f), new ReplayQueue(),
        () -> "s", 1000, LDLogger.none(), Executors.newSingleThreadScheduledExecutor());
    source.failure = new IllegalStateException("boom");
    loop.tick();
    assertFalse(loop.isPaused());
    source.failure = null;
    source.error = new OutOfMemoryError("test");
    loop.tick();
    assertTrue(loop.isPaused());
    loop.stop();
  }

  @Test
  public void theLoopRunsOnItsOwnThreadUntilStopped() throws Exception {
    FakeSource source = new FakeSource();
    CaptureLoop loop = new CaptureLoop(Collections.singletonList(source), new FrameEncoder(0.3f), new ReplayQueue(),
        () -> "s", 20, LDLogger.none());
    loop.start();
    Thread.sleep(200);
    loop.stop();
    int asked = source.asked.get();
    assertTrue(asked > 0);
    Thread.sleep(100);
    assertEquals(asked, source.asked.get());
    Thread.currentThread().interrupt();
    new CaptureLoop(Collections.singletonList(source), new FrameEncoder(0.3f), new ReplayQueue(), () -> "s", 20,
        LDLogger.none()).stop();
    assertTrue(Thread.interrupted());
  }

  @Test
  public void stoppingWhenInterruptedStopsAtOnce() {
    CaptureLoop loop = new CaptureLoop(Collections.singletonList(new FakeSource()), new FrameEncoder(0.3f),
        new ReplayQueue(), () -> "s", 20, LDLogger.none());
    loop.start();
    Thread.currentThread().interrupt();
    loop.stop();
    assertTrue(Thread.interrupted());
  }

  // -- Swing --

  private static void assumeDisplay() {
    Assume.assumeFalse("needs a display", GraphicsEnvironment.isHeadless());
  }

  private static final SwingCapture.Masks NO_MASKS = (content, x, y, scale, out) -> { };

  private JFrame frame(Color color) throws Exception {
    AtomicReference<JFrame> made = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      JFrame f = new JFrame("capture");
      JPanel panel = new JPanel();
      panel.setBackground(color);
      f.setContentPane(panel);
      f.setSize(240, 160);
      f.setLocation(100, 100);
      f.setVisible(true);
      windows.add(f);
      made.set(f);
    });
    return made.get();
  }

  @Test
  public void theProductionCaptureWaitsForAwt() {
    assertNull(new SwingCapture(1, NO_MASKS, LDLogger.none(), () -> false).capture(100));
  }

  @Test
  public void withoutAwtNothingIsAsked() {
    SwingCapture capture = new SwingCapture(1, NO_MASKS, LDLogger.none(), () -> false, () -> {
      throw new AssertionError("AWT must not be asked");
    });
    assertNull(capture.capture(100));
  }

  @Test
  public void theActiveWindowIsPaintedWithItsDialogOnTop() throws Exception {
    assumeDisplay();
    JFrame frame = frame(Color.RED);
    AtomicReference<JDialog> dialog = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      JDialog d = new JDialog(frame, "dialog");
      JPanel panel = new JPanel();
      panel.setBackground(Color.BLUE);
      d.setContentPane(panel);
      d.setUndecorated(true);
      d.setSize(40, 30);
      d.setLocation(frame.getRootPane().getLocationOnScreen().x + 10, frame.getRootPane().getLocationOnScreen().y + 10);
      d.setVisible(true);
      windows.add(d);
      dialog.set(d);
    });
    List<String> painted = new ArrayList<>();
    SwingCapture capture = new SwingCapture(1, (content, x, y, scale, out) -> {
      painted.add(content.getClass().getSimpleName() + " " + x + "," + y);
      out.add(new Rectangle(x, y, 1, 1));
    }, LDLogger.none(), () -> true, dialog::get);
    CaptureSource.RawFrame raw = capture.capture(5000);
    assertNotNull(raw);
    assertEquals(frame.getRootPane().getWidth(), raw.image.getWidth());
    assertEquals(0xffff0000, raw.image.getRGB(raw.image.getWidth() - 5, raw.image.getHeight() - 5));
    assertEquals(0xff0000ff, raw.image.getRGB(20, 20));
    assertThat(painted, contains("JRootPane 0,0", "JRootPane 10,10"));
    assertThat("masked as painted, so nothing is left to mask", raw.masks, empty());
    assertEquals("the dialog's own mask", CaptureLoop.MASK_COLOR.getRGB(), raw.image.getRGB(10, 10));
  }

  @Test
  public void aDialogOverAMaskedComponentStaysVisible() throws Exception {
    assumeDisplay();
    AtomicReference<JFrame> made = new AtomicReference<>();
    AtomicReference<JDialog> dialog = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      JFrame f = new JFrame("under");
      f.setContentPane(new javax.swing.JScrollPane(new javax.swing.JTextArea("private notes", 20, 40)));
      f.setSize(300, 200);
      f.setVisible(true);
      windows.add(f);
      made.set(f);
      JDialog d = new JDialog(f, "over");
      JPanel blue = new JPanel();
      blue.setBackground(Color.BLUE);
      d.setContentPane(blue);
      d.setUndecorated(true);
      d.setSize(60, 40);
      java.awt.Point root = f.getRootPane().getLocationOnScreen();
      d.setLocation(root.x + 100, root.y + 80);
      d.setVisible(true);
      windows.add(d);
      dialog.set(d);
    });
    CaptureSource.RawFrame raw = new SwingCapture(1, new SwingMasks(MaskRules.strict()), LDLogger.none(), () -> true,
        dialog::get).capture(5000);
    assertEquals("the dialog, not the mask under it", 0xff0000ff, raw.image.getRGB(130, 100));
    assertEquals("the masked notes around it", CaptureLoop.MASK_COLOR.getRGB(), raw.image.getRGB(40, 40));
  }

  @Test
  public void aDialogWhoseOwnerIsHiddenIsTheFrameAndHiddenDialogsAreLeftOut() throws Exception {
    assumeDisplay();
    JFrame frame = frame(Color.RED);
    AtomicReference<JDialog> shown = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      JDialog hidden = new JDialog(frame, "hidden"); // owned, never shown
      windows.add(hidden);
      JFrame invisibleOwner = new JFrame("owner");
      windows.add(invisibleOwner);
      JDialog d = new JDialog(invisibleOwner, "orphan");
      d.setSize(90, 60);
      d.setVisible(true);
      windows.add(d);
      shown.set(d);
    });
    List<String> painted = new ArrayList<>();
    CaptureSource.RawFrame raw = new SwingCapture(1, (content, x, y, scale, out) -> painted.add(x + "," + y),
        LDLogger.none(), () -> true, shown::get).capture(5000);
    assertEquals(shown.get().getRootPane().getWidth(), raw.image.getWidth());
    assertThat(painted, contains("0,0"));
    assertEquals("the frame's own dialogs", 1, frame.getOwnedWindows().length);
  }

  @Test
  public void theScaleSizesTheFrame() throws Exception {
    assumeDisplay();
    JFrame frame = frame(Color.GREEN);
    CaptureSource.RawFrame raw = new SwingCapture(0.5, NO_MASKS, LDLogger.none(), () -> true, () -> frame)
        .capture(5000);
    assertEquals(Math.round(frame.getRootPane().getWidth() * 0.5), raw.image.getWidth());
  }

  @Test
  public void noActiveWindowOrAnEmptyOneIsNoFrame() throws Exception {
    assumeDisplay();
    assertNull(new SwingCapture(1, NO_MASKS, LDLogger.none(), () -> true, () -> null).capture(5000));
    JFrame frame = frame(Color.GREEN);
    SwingUtilities.invokeAndWait(() -> frame.setVisible(false));
    assertNull(new SwingCapture(1, NO_MASKS, LDLogger.none(), () -> true, () -> frame).capture(5000));
    SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
    assertNull("too small to see", new SwingCapture(0.001, NO_MASKS, LDLogger.none(), () -> true, () -> frame)
        .capture(5000));
  }

  @Test
  public void aBusyEventThreadOrAFailedPaintSkipsTheFrame() throws Exception {
    assumeDisplay();
    JFrame frame = frame(Color.GREEN);
    CountDownLatch release = new CountDownLatch(1);
    SwingUtilities.invokeLater(() -> {
      // Blocks the event thread until released, even if it is interrupted, as it can be on Linux.
      long end = System.currentTimeMillis() + 5000;
      boolean interrupted = false;
      while (release.getCount() > 0 && System.currentTimeMillis() < end) {
        try {
          release.await(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    });
    assertNull(new SwingCapture(1, NO_MASKS, LDLogger.none(), () -> true, () -> frame).capture(100));
    release.countDown();

    SwingCapture.Masks failing = (content, x, y, scale, out) -> {
      throw new IllegalStateException("boom");
    };
    assertNull(new SwingCapture(1, failing, LDLogger.none(), () -> true, () -> frame).capture(5000));

    Thread.currentThread().interrupt();
    assertNull(new SwingCapture(1, NO_MASKS, LDLogger.none(), () -> true, () -> frame).capture(5000));
    assertTrue(Thread.interrupted());
  }

  @Test
  public void aPlainAwtWindowIsPaintedWhole() throws Exception {
    assumeDisplay();
    AtomicReference<java.awt.Frame> made = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      java.awt.Frame f = new java.awt.Frame("awt");
      f.setBackground(Color.ORANGE);
      f.setSize(120, 90);
      f.setVisible(true);
      windows.add(f);
      made.set(f);
    });
    Component content = SwingCapture.content(made.get());
    assertEquals(made.get(), content);
    CaptureSource.RawFrame raw = new SwingCapture(1, NO_MASKS, LDLogger.none(), () -> true, made::get)
        .capture(5000);
    assertNotNull(raw);
    assertEquals(120, raw.image.getWidth());
  }
  @Test
  public void aCapturedTextFieldComesOutGrey() throws Exception {
    assumeDisplay();
    AtomicReference<JFrame> made = new AtomicReference<>();
    AtomicReference<javax.swing.JTextField> field = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      JFrame f = new JFrame("masked");
      JPanel panel = new JPanel(null);
      panel.setBackground(Color.WHITE);
      javax.swing.JTextField text = new javax.swing.JTextField("jane@example.com");
      text.setBounds(20, 20, 160, 28);
      panel.add(text);
      f.setContentPane(panel);
      f.setSize(240, 160);
      f.setVisible(true);
      windows.add(f);
      made.set(f);
      field.set(text);
    });
    CaptureSource.RawFrame raw = new SwingCapture(1, new SwingMasks(MaskRules.strict()), LDLogger.none(), () -> true,
        made::get).capture(5000);
    CaptureLoop.mask(raw);
    java.awt.Point at = SwingUtilities.convertPoint(field.get().getParent(), field.get().getLocation(),
        made.get().getRootPane());
    for (int x = at.x; x < at.x + 160; x += 7) {
      for (int y = at.y; y < at.y + 28; y += 3) {
        assertEquals("every pixel of the field", CaptureLoop.MASK_COLOR.getRGB(), raw.image.getRGB(x, y));
      }
    }
    assertEquals("the panel around it shows", 0xffffffff, raw.image.getRGB(at.x + 200, at.y + 60));
  }
}
