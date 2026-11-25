package com.launchdarkly.observability.internal.replay;

import org.junit.Test;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** How the mouse is recorded: thinned, grouped, and placed on the last frame. */
public class PointerTest {
  private final ReplayQueue queue = new ReplayQueue();
  private final PointerRecorder recorder = new PointerRecorder(queue, () -> "s1");

  /** A frame whose top left corner is at (100, 50) on the screen, at this scale. */
  private void frameAt(double scale) {
    recorder.frameTaken(new CaptureSource.RawFrame(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB),
        Collections.emptyList(), 0, 100, 50, scale));
  }

  private List<ReplayItem.Pointer> sent() {
    List<ReplayItem.Pointer> out = new ArrayList<>();
    for (ReplayItem item : queue.peek(Long.MAX_VALUE, Integer.MAX_VALUE)) {
      out.add((ReplayItem.Pointer) item);
    }
    return out;
  }

  private static String describe(ReplayItem.Pointer pointer) {
    StringBuilder s = new StringBuilder(pointer.action.toString());
    for (ReplayItem.Position p : pointer.positions) {
      s.append(' ').append(p.x).append(',').append(p.y).append('@').append(p.timestamp);
    }
    return s.toString();
  }

  private List<String> described() {
    List<String> out = new ArrayList<>();
    sent().forEach(p -> out.add(describe(p)));
    return out;
  }

  @Test
  public void nothingIsRecordedUntilRecordingStartsAndAFrameIsTaken() {
    recorder.pointer(ReplayItem.Pointer.Action.DOWN, 110, 60, 1);
    recorder.setRecording(true);
    recorder.pointer(ReplayItem.Pointer.Action.DOWN, 110, 60, 2); // no frame to place it on
    assertEquals(0, queue.size());
    frameAt(1);
    recorder.pointer(ReplayItem.Pointer.Action.DOWN, 110, 60, 3);
    assertEquals(Collections.singletonList("DOWN 10,10@3"), described());
  }

  @Test
  public void positionsAreMeasuredFromTheFrameAndScaled() {
    recorder.setRecording(true);
    frameAt(0.5);
    recorder.pointer(ReplayItem.Pointer.Action.UP, 300, 250, 7);
    assertEquals(java.util.Arrays.asList("MOVE 100,100@7", "UP 100,100@7"), described());
  }

  @Test
  public void movesAreThinnedAndSentTogether() {
    recorder.setRecording(true);
    frameAt(1);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 100, 50, 1000); // kept, and sent: the first
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 120, 50, 1030); // far, but only 30 ms after the last kept
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 105, 50, 1100); // late enough, but too close
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 140, 50, 1200); // kept
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 160, 50, 1300); // kept, and 300 ms since the last send
    assertEquals(java.util.Arrays.asList("MOVE 0,0@1000", "MOVE 40,0@1200 60,0@1300"), described());
  }

  @Test
  public void aFlushSendsTheMovesKept() {
    recorder.setRecording(true);
    frameAt(1);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 100, 50, 1000);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 140, 50, 1100);
    recorder.flush();
    recorder.flush(); // nothing more
    assertEquals(java.util.Arrays.asList("MOVE 0,0@1000", "MOVE 40,0@1100"), described());
  }

  @Test
  public void aPressSendsTheMovesBeforeIt() {
    recorder.setRecording(true);
    frameAt(1);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 100, 50, 1000);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 140, 50, 1100);
    recorder.pointer(ReplayItem.Pointer.Action.DOWN, 140, 50, 1150);
    assertEquals(java.util.Arrays.asList("MOVE 0,0@1000", "MOVE 40,0@1100", "DOWN 40,0@1150"), described());
  }

  @Test
  public void aReleaseEndsTheMoveAtItsPositionOnce() {
    recorder.setRecording(true);
    frameAt(1);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 100, 50, 1000);
    recorder.pointer(ReplayItem.Pointer.Action.UP, 100, 50, 1000); // where the last kept move was
    // After a release, the next move starts a new sequence and is kept and sent at once.
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 101, 50, 1001);
    assertEquals(java.util.Arrays.asList("MOVE 0,0@1000", "UP 0,0@1000", "MOVE 1,0@1001"), described());
  }

  @Test
  public void aReleaseElsewhereEndsTheMoveThere() {
    recorder.setRecording(true);
    frameAt(1);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 100, 50, 1000);
    recorder.pointer(ReplayItem.Pointer.Action.UP, 100, 51, 1010); // dragged a pixel down
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 100, 50, 1100);
    recorder.pointer(ReplayItem.Pointer.Action.UP, 101, 50, 1110); // and a pixel across
    assertEquals(java.util.Arrays.asList("MOVE 0,0@1000", "MOVE 0,1@1010", "UP 0,1@1010", "MOVE 0,0@1100",
        "MOVE 1,0@1110", "UP 1,0@1110"), described());
  }

  @Test
  public void stoppingDropsTheMovesNotSent() {
    recorder.setRecording(true);
    frameAt(1);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 100, 50, 1000);
    recorder.pointer(ReplayItem.Pointer.Action.MOVE, 140, 50, 1100);
    recorder.setRecording(false);
    recorder.flush();
    recorder.pointer(ReplayItem.Pointer.Action.DOWN, 140, 50, 1150);
    assertEquals(Collections.singletonList("MOVE 0,0@1000"), described());
  }

  @Test
  public void eachItemIsInTheCurrentSession() {
    recorder.setRecording(true);
    frameAt(1);
    recorder.pointer(ReplayItem.Pointer.Action.DOWN, 100, 50, 1);
    assertEquals("s1", sent().get(0).sessionId);
  }

  @Test
  public void swingMouseEventsBecomePointerActions() {
    assertEquals(ReplayItem.Pointer.Action.DOWN, SwingCapture.action(MouseEvent.MOUSE_PRESSED));
    assertEquals(ReplayItem.Pointer.Action.UP, SwingCapture.action(MouseEvent.MOUSE_RELEASED));
    assertEquals(ReplayItem.Pointer.Action.MOVE, SwingCapture.action(MouseEvent.MOUSE_MOVED));
    assertEquals(ReplayItem.Pointer.Action.MOVE, SwingCapture.action(MouseEvent.MOUSE_DRAGGED));
    assertNull(SwingCapture.action(MouseEvent.MOUSE_CLICKED));
    assertNull(SwingCapture.action(MouseEvent.MOUSE_ENTERED));
  }

  @Test
  public void theSwingCaptureReportsTheMouseOnTheScreen() {
    List<String> seen = new ArrayList<>();
    SwingCapture capture = new SwingCapture(1, (c, x, y, s, out) -> { }, com.launchdarkly.logging.LDLogger.none(),
        () -> false);
    capture.pointer = (action, x, y, t) -> seen.add(action + " " + x + "," + y + "@" + t);
    JPanel panel = new JPanel();
    capture.mouseEvent(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 5, 0, 1, 2, 30, 40, 0, false,
        MouseEvent.NOBUTTON));
    capture.mouseEvent(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED, 6, 0, 1, 2, 30, 40, 1, false,
        MouseEvent.BUTTON1));
    assertEquals(Collections.singletonList("MOVE 30.0,40.0@5"), seen);
  }

  @Test
  public void theSwingCaptureListensOnlyOnceAWTIsInUseAndStopsListening() {
    SwingCapture idle = new SwingCapture(1, (c, x, y, s, out) -> { }, com.launchdarkly.logging.LDLogger.none(),
        () -> false);
    idle.pointer = (action, x, y, t) -> { };
    assertNull(idle.capture(10)); // AWT not in use: nothing attached
    idle.stop(); // nothing to stop

    java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
    int before = toolkit.getAWTEventListeners().length;
    SwingCapture capture = new SwingCapture(1, (c, x, y, s, out) -> { }, com.launchdarkly.logging.LDLogger.none(),
        () -> true, () -> null);
    capture.pointer = (action, x, y, t) -> { };
    assertNull(capture.capture(1000)); // no active window
    capture.capture(1000); // attaches once
    assertEquals(before + 1, toolkit.getAWTEventListeners().length);
    capture.stop();
    assertEquals(before, toolkit.getAWTEventListeners().length);

    SwingCapture noPointer = new SwingCapture(1, (c, x, y, s, out) -> { }, com.launchdarkly.logging.LDLogger.none(),
        () -> true, () -> null);
    noPointer.capture(1000);
    assertEquals(before, toolkit.getAWTEventListeners().length);
  }
}
