package com.launchdarkly.observability.internal.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Records the mouse for the replay: presses, releases and movement, in the pixels of the last frame.
 * The backend lists only sessions with user interactions, and on a desktop most of them are the
 * mouse moving, so every move counts, not only drags.
 * <p>
 * Moves are thinned and grouped as on Android: a position is kept only once the mouse has gone more
 * than 12 pixels and 40 milliseconds from the last one kept, and the kept positions go out together
 * every 240 milliseconds, or when the mouse is pressed or released, or at {@link #flush}, which the
 * capture loop calls each frame so that the last moves before the mouse stops are not held back.
 * <p>
 * Called on the toolkits' UI threads, so it does little and never blocks for long.
 */
final class PointerRecorder implements PointerSink {
  /** 12 pixels, squared, as on Android and iOS. */
  static final int MOVE_DISTANCE_SQUARED = 144;
  static final long MOVE_INTERVAL_MILLIS = 40;
  static final long EMIT_PERIOD_MILLIS = 240;

  /** Where the last frame was taken: what a screen position is measured from. */
  private static final class Origin {
    final double x;
    final double y;
    final double scale;

    Origin(double x, double y, double scale) {
      this.x = x;
      this.y = y;
      this.scale = scale;
    }
  }

  private final ReplayQueue queue;
  private final Supplier<String> sessions;

  private boolean recording; // guarded by this
  private volatile Origin origin;
  // Guarded by this.
  private final List<ReplayItem.Position> kept = new ArrayList<>();
  private ReplayItem.Position lastKept;
  private long lastEmit;

  PointerRecorder(ReplayQueue queue, Supplier<String> sessions) {
    this.queue = queue;
    this.sessions = sessions;
  }

  /** Starts or stops recording. Stopping drops the moves not yet sent. */
  synchronized void setRecording(boolean recording) {
    this.recording = recording;
    if (!recording) {
      reset();
    }
  }

  /** A frame was taken: positions are measured from it from now on. On the capture thread. */
  void frameTaken(CaptureSource.RawFrame frame) {
    origin = new Origin(frame.originX, frame.originY, frame.scale);
  }

  @Override
  public synchronized void pointer(ReplayItem.Pointer.Action action, double screenX, double screenY,
      long timestamp) {
    Origin at = origin;
    if (!recording || at == null) {
      return; // nothing to place it on before the first frame
    }
    int x = (int) Math.round((screenX - at.x) * at.scale);
    int y = (int) Math.round((screenY - at.y) * at.scale);
    ReplayItem.Position position = new ReplayItem.Position(x, y, timestamp);
    switch (action) {
      case DOWN:
        emitKept();
        offer(ReplayItem.Pointer.Action.DOWN, position);
        break;
      case UP:
        // The release is where the move ends, unless the move already ended there.
        if (lastKept == null || lastKept.x != x || lastKept.y != y) {
          kept.add(position);
        }
        emitKept();
        reset();
        offer(ReplayItem.Pointer.Action.UP, position);
        break;
      default:
        move(position);
    }
  }

  /** Sends the moves kept so far. */
  synchronized void flush() {
    emitKept();
  }

  private void move(ReplayItem.Position position) {
    if (lastKept == null || (distanceSquared(position, lastKept) > MOVE_DISTANCE_SQUARED
        && position.timestamp - lastKept.timestamp > MOVE_INTERVAL_MILLIS)) {
      kept.add(position);
      lastKept = position;
    }
    if (position.timestamp - lastEmit > EMIT_PERIOD_MILLIS) {
      emitKept();
      lastEmit = position.timestamp;
    }
  }

  private void emitKept() {
    if (kept.isEmpty()) {
      return;
    }
    queue.offer(new ReplayItem.Pointer(ReplayItem.Pointer.Action.MOVE, new ArrayList<>(kept), sessions.get()));
    kept.clear();
  }

  private void offer(ReplayItem.Pointer.Action action, ReplayItem.Position position) {
    queue.offer(new ReplayItem.Pointer(action, Collections.singletonList(position), sessions.get()));
  }

  private void reset() {
    kept.clear();
    lastKept = null;
    lastEmit = 0;
  }

  private static int distanceSquared(ReplayItem.Position a, ReplayItem.Position b) {
    int dx = a.x - b.x;
    int dy = a.y - b.y;
    return dx * dx + dy * dy;
  }
}
