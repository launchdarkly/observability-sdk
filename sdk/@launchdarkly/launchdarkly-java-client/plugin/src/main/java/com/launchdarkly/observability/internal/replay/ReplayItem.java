package com.launchdarkly.observability.internal.replay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Something recorded for replay, waiting in the queue to be sent. */
abstract class ReplayItem {
  final long timestamp;
  /** The session it was recorded in, or null for the session of the last frame. */
  final String sessionId;

  ReplayItem(long timestamp, String sessionId) {
    this.timestamp = timestamp;
    this.sessionId = sessionId;
  }

  /** What the item costs in the queue, roughly its size. */
  abstract int cost();

  /** A captured frame. */
  static final class Frame extends ReplayItem {
    final ExportFrame frame;

    Frame(ExportFrame frame) {
      super(frame.timestamp, frame.sessionId);
      this.frame = frame;
    }

    @Override
    int cost() {
      return frame.cost();
    }
  }

  /** A pointer position, in the frame's coordinates. */
  static final class Position {
    final int x;
    final int y;
    final long timestamp;

    Position(int x, int y, long timestamp) {
      this.x = x;
      this.y = y;
      this.timestamp = timestamp;
    }
  }

  /** A mouse button going down or up, or the mouse moving with it down. */
  static final class Pointer extends ReplayItem {
    enum Action { DOWN, UP, MOVE }

    final Action action;
    final List<Position> positions;

    Pointer(Action action, List<Position> positions, String sessionId) {
      super(positions.get(0).timestamp, sessionId);
      this.action = action;
      this.positions = Collections.unmodifiableList(positions);
    }

    @Override
    int cost() {
      return 300;
    }
  }

  /**
   * A custom event shown on the replay's timeline, such as a click, a screen change or a tracked
   * event. Its payload is already JSON.
   */
  static class Custom extends ReplayItem {
    final String tag;
    final String payloadJson;

    Custom(String tag, String payloadJson, long timestamp, String sessionId) {
      super(timestamp, sessionId);
      this.tag = tag;
      this.payloadJson = payloadJson;
    }

    @Override
    int cost() {
      return 300 + payloadJson.length();
    }
  }

  /** Who the session belongs to: an {@code Identify} event, and the session's identity on the backend. */
  static final class Identify extends Custom {
    final Map<String, String> attributes;

    Identify(Map<String, String> attributes, long timestamp, String sessionId) {
      super(RRWeb.TAG_IDENTIFY, JsonWriter.quoted(new JsonWriter().stringMap(attributes).toString()), timestamp,
          sessionId);
      this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** The name the session is listed under. */
    String userIdentifier() {
      String key = attributes.get("key");
      return key != null ? key : "unknown";
    }

    @Override
    int cost() {
      return attributes.size() * 100;
    }
  }
}
