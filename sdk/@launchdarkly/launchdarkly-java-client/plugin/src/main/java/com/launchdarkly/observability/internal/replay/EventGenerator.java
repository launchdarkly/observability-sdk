package com.launchdarkly.observability.internal.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns frames, pointer actions and custom events into rrweb events, the format the replay player
 * reads. Ported from the Android plugin's {@code RRWebEventGenerator}.
 * <p>
 * The replay is a document whose body holds the screen as absolutely placed {@code <img>} tiles. A
 * full snapshot draws a frame afresh; later frames add tiles over it and remove tiles they cover.
 * Each event needs an increasing sequence id and each tile a node id, and the generator keeps both,
 * with what each tile's node id is. That state can be saved and restored, so an export that fails
 * regenerates identical events on retry. Not thread-safe: the exporter serializes its use.
 * <p>
 * Each event is written into one {@link JsonByteWriter}, passed down to what writes its parts, so
 * an event's image data is copied once, into the bytes that are sent.
 */
final class EventGenerator {
  static final int INITIAL_NODE_ID = 16;
  private static final String BODY_STYLE = "position:relative;";
  private static final String CLICK_SELECTOR_FALLBACK = "view";
  private static final String IMAGE_SOURCE_PREFIX = "data:image/jpeg;base64,";
  /** Room for an event's fields around its data. */
  private static final int EVENT_OVERHEAD = 96;
  /** Room for a tile's fields around its image data. */
  private static final int TILE_OVERHEAD = 256;

  /** An encoded event and when it happened, which the backend wants them sorted by. */
  static final class Event {
    final long timestamp;
    /** The event as UTF-8 JSON, written into the payload as it is. */
    final byte[] json;

    Event(long timestamp, byte[] json) {
      this.timestamp = timestamp;
      this.json = json;
    }
  }

  /** Everything the generator remembers, for rolling back. */
  static final class State {
    final int lastSid;
    final long canvasSize;
    final int lastNodeId;
    final Integer imageNodeId;
    final Integer bodyNodeId;
    final Integer knownKeyFrameId;
    final Map<ExportFrame.Signature, Integer> nodeIds;

    State(int lastSid, long canvasSize, int lastNodeId, Integer imageNodeId, Integer bodyNodeId,
        Integer knownKeyFrameId, Map<ExportFrame.Signature, Integer> nodeIds) {
      this.lastSid = lastSid;
      this.canvasSize = canvasSize;
      this.lastNodeId = lastNodeId;
      this.imageNodeId = imageNodeId;
      this.bodyNodeId = bodyNodeId;
      this.knownKeyFrameId = knownKeyFrameId;
      this.nodeIds = Collections.unmodifiableMap(new HashMap<>(nodeIds));
    }
  }

  private final String title;
  /** The player's fixed cost of drawing a tile, on top of its image data. */
  private final int drawCost;

  private int lastSid;
  /** How much image data the player holds since the last full snapshot. */
  long canvasSize;
  private int lastNodeId = INITIAL_NODE_ID;
  private Integer imageNodeId;
  private Integer bodyNodeId;
  private Integer knownKeyFrameId;
  private final Map<ExportFrame.Signature, Integer> nodeIds = new HashMap<>();

  EventGenerator(String title, int drawCost) {
    this.title = title;
    this.drawCost = drawCost;
  }

  State getState() {
    return new State(lastSid, canvasSize, lastNodeId, imageNodeId, bodyNodeId, knownKeyFrameId, nodeIds);
  }

  void restoreState(State state) {
    lastSid = state.lastSid;
    canvasSize = state.canvasSize;
    lastNodeId = state.lastNodeId;
    imageNodeId = state.imageNodeId;
    bodyNodeId = state.bodyNodeId;
    knownKeyFrameId = state.knownKeyFrameId;
    nodeIds.clear();
    nodeIds.putAll(state.nodeIds);
  }

  private int nextSid() {
    return ++lastSid;
  }

  private int nextNodeId() {
    return ++lastNodeId;
  }

  /**
   * Starts an event of {@code type}, sized for {@code dataSize} bytes of data; the caller writes the
   * data, then {@link #endEvent} closes it.
   */
  private static JsonByteWriter beginEvent(int type, long dataSize) {
    return new JsonByteWriter(dataSize + EVENT_OVERHEAD).beginObject()
        .name("type").value(type)
        .name("data");
  }

  /** Closes an event begun by {@link #beginEvent}, giving it the next sequence id. */
  private Event endEvent(JsonByteWriter out, long timestamp) {
    out.name("timestamp").value(timestamp)
        .name("_sid").value(nextSid())
        .endObject();
    return new Event(timestamp, out.toByteArray());
  }

  // -- frames --

  /**
   * A full snapshot: the document, with the frame's tiles as its body, then the viewport. Sent for
   * a session's first frame, after a resize, and when the player holds too much image data.
   */
  List<Event> fullSnapshot(ExportFrame frame) {
    if (frame.addImages.isEmpty()) {
      return Collections.emptyList();
    }
    nodeIds.clear();
    knownKeyFrameId = frame.keyFrameId;
    List<Event> events = new ArrayList<>(3);
    JsonByteWriter meta = beginEvent(RRWeb.META, 64).beginObject()
        .name("width").value(frame.width + RRWeb.DOCUMENT_PADDING * 2)
        .name("height").value(frame.height + RRWeb.DOCUMENT_PADDING * 2)
        .endObject();
    events.add(endEvent(meta, frame.timestamp));

    // The ids are given in the order Android gives them: the document, head and body, each tile, then html.
    lastNodeId = 0;
    int documentId = nextNodeId();
    int headId = nextNodeId();
    int bodyId = nextNodeId();
    int[] tileIds = new int[frame.addImages.size()];
    long size = 0;
    for (int i = 0; i < tileIds.length; i++) {
      tileIds[i] = nextNodeId();
      size += frame.addImages.get(i).base64.length;
    }
    int htmlId = nextNodeId();

    JsonByteWriter out = beginEvent(RRWeb.FULL_SNAPSHOT, size + (long) tileIds.length * TILE_OVERHEAD + 1024)
        .beginObject()
        .name("node").beginObject()
        .name("type").value(RRWeb.DOCUMENT_NODE)
        .name("id").value(documentId)
        .name("childNodes").beginArray()
        .beginObject()
        .name("type").value(RRWeb.ELEMENT_NODE)
        .name("id").value(htmlId)
        .name("tagName").value("html")
        .name("attributes").beginObject().name("lang").value("en").endObject()
        .name("childNodes").beginArray()
        .beginObject()
        .name("type").value(RRWeb.ELEMENT_NODE)
        .name("id").value(headId)
        .name("tagName").value("head")
        .name("attributes").beginObject().endObject()
        .name("childNodes").beginArray().endArray()
        .endObject()
        .beginObject()
        .name("type").value(RRWeb.ELEMENT_NODE)
        .name("id").value(bodyId)
        .name("tagName").value("body")
        .name("attributes").beginObject().name("style").value(BODY_STYLE).endObject()
        .name("childNodes").beginArray();
    for (int i = 0; i < tileIds.length; i++) {
      writeTileNode(out, tileIds[i], frame.addImages.get(i));
    }
    out.endArray().endObject().endArray().endObject().endArray().endObject().endObject();
    events.add(endEvent(out, frame.timestamp));

    imageNodeId = tileIds[0];
    bodyNodeId = bodyId;
    canvasSize = size + drawCost;

    JsonByteWriter viewport = beginEvent(RRWeb.CUSTOM, 256).beginObject()
        .name("tag").value(RRWeb.TAG_VIEWPORT)
        .name("payload").beginObject()
        .name("width").value(frame.width)
        .name("height").value(frame.height)
        .name("availWidth").value(frame.width)
        .name("availHeight").value(frame.height)
        .name("colorDepth").value(30)
        .name("pixelDepth").value(30)
        .name("orientation").value(0)
        .endObject()
        .endObject();
    events.add(endEvent(viewport, frame.timestamp));
    return events;
  }

  /**
   * The changes since the last frame: tiles added over it and tiles removed. A frame that builds
   * on a keyframe the player never saw is dropped, since it would draw on the wrong picture.
   */
  List<Event> incremental(ExportFrame frame) {
    if (bodyNodeId == null) {
      return Collections.emptyList();
    }
    if (frame.keyframe) {
      knownKeyFrameId = frame.keyFrameId;
    } else if (!Integer.valueOf(frame.keyFrameId).equals(knownKeyFrameId)) {
      return Collections.emptyList();
    }
    JsonByteWriter out = beginEvent(RRWeb.INCREMENTAL_SNAPSHOT,
        frame.cost() + (long) frame.addImages.size() * TILE_OVERHEAD + frame.removeImages.size() * 32L + 512)
        .beginObject()
        .name("source").value(RRWeb.SOURCE_MUTATION)
        .name("texts").beginArray().endArray()
        .name("attributes").beginArray().endArray()
        .name("removes").beginArray();
    for (ExportFrame.Signature removal : frame.removeImages) {
      Integer id = nodeIds.get(removal);
      if (id != null) {
        out.beginObject().name("parentId").value(bodyNodeId).name("id").value(id).endObject();
      }
    }
    out.endArray().name("adds").beginArray();
    if (frame.keyframe) {
      nodeIds.clear();
    }
    long size = 0;
    Integer firstId = null;
    for (ExportFrame.AddImage image : frame.addImages) {
      int id = nextNodeId();
      if (firstId == null) {
        firstId = id;
      }
      out.beginObject().name("parentId").value(bodyNodeId).name("node");
      writeTileNode(out, id, image);
      out.endObject();
      size += image.base64.length;
    }
    out.endArray().endObject();
    if (frame.keyframe && firstId != null) {
      imageNodeId = firstId;
    }
    canvasSize += size + drawCost;
    return Collections.singletonList(endEvent(out, frame.timestamp));
  }

  /** Writes a tile as an {@code <img>} node, its image data copied in as it is, and remembers its id. */
  private void writeTileNode(JsonByteWriter out, int id, ExportFrame.AddImage image) {
    if (image.signature != null) {
      nodeIds.put(image.signature, id);
    }
    out.beginObject()
        .name("type").value(RRWeb.ELEMENT_NODE)
        .name("id").value(id)
        .name("tagName").value("img")
        .name("attributes").beginObject()
        .name("src").asciiValue(IMAGE_SOURCE_PREFIX, image.base64)
        .name("width").value(Integer.toString(image.width))
        .name("height").value(Integer.toString(image.height))
        .name("style").value("position:absolute;left:" + image.left + "px;top:" + image.top
            + "px;pointer-events:none;")
        .endObject()
        .name("childNodes").beginArray().endArray()
        .endObject();
  }

  // -- the pointer --

  /** The mouse going down or up, or moving, as rrweb mouse events over the screen image. */
  List<Event> pointer(ReplayItem.Pointer pointer) {
    List<Event> events = new ArrayList<>(pointer.positions.size());
    switch (pointer.action) {
      case DOWN:
        events.add(mouseInteraction(RRWeb.MOUSE_DOWN, pointer.positions.get(0)));
        break;
      case UP:
        events.add(mouseInteraction(RRWeb.MOUSE_UP, pointer.positions.get(pointer.positions.size() - 1)));
        break;
      default:
        for (ReplayItem.Position position : pointer.positions) {
          JsonByteWriter out = beginEvent(RRWeb.INCREMENTAL_SNAPSHOT, 128).beginObject()
              .name("source").value(RRWeb.SOURCE_MOUSE_MOVE)
              .name("positions").beginArray().beginObject();
          if (imageNodeId != null) {
            out.name("id").value(imageNodeId);
          }
          out.name("timeOffset").value(0)
              .name("x").value(position.x + RRWeb.DOCUMENT_PADDING)
              .name("y").value(position.y + RRWeb.DOCUMENT_PADDING)
              .endObject().endArray().endObject();
          events.add(endEvent(out, position.timestamp));
        }
    }
    return events;
  }

  private Event mouseInteraction(int type, ReplayItem.Position position) {
    return mouseInteraction(type, position.x + RRWeb.DOCUMENT_PADDING, position.y + RRWeb.DOCUMENT_PADDING,
        position.timestamp);
  }

  private Event mouseInteraction(int type, int x, int y, long timestamp) {
    JsonByteWriter out = beginEvent(RRWeb.INCREMENTAL_SNAPSHOT, 128).beginObject()
        .name("source").value(RRWeb.SOURCE_MOUSE_INTERACTION)
        .name("texts").beginArray().endArray()
        .name("type").value(type);
    if (imageNodeId != null) {
      out.name("id").value(imageNodeId);
    }
    out.name("x").value(x).name("y").value(y).endObject();
    return endEvent(out, timestamp);
  }

  // -- custom events --

  /** A custom event on the replay's timeline. */
  Event custom(ReplayItem.Custom custom) {
    JsonByteWriter out = beginEvent(RRWeb.CUSTOM, custom.payloadJson.length() * 3L + 64).beginObject()
        .name("tag").value(custom.tag)
        .name("payload").raw(custom.payloadJson)
        .endObject();
    return endEvent(out, custom.timestamp);
  }

  /**
   * What the player needs, after a session's first push, to start playing: a {@code Reload} and a
   * click on the screen image, and the launch when there is one to report. Empty until a frame has
   * been sent.
   */
  List<Event> wakeUp(long timestamp, ReplayItem.Custom launch) {
    if (imageNodeId == null) {
      return Collections.emptyList();
    }
    List<Event> events = new ArrayList<>(4);
    JsonByteWriter reload = beginEvent(RRWeb.CUSTOM, title.length() * 6L + 64).beginObject()
        .name("tag").value(RRWeb.TAG_RELOAD)
        .name("payload").value(title)
        .endObject();
    events.add(endEvent(reload, timestamp));
    if (launch != null) {
      events.add(custom(launch));
    }
    events.add(mouseInteraction(RRWeb.MOUSE_DOWN, RRWeb.DOCUMENT_PADDING, RRWeb.DOCUMENT_PADDING, timestamp));
    events.add(mouseInteraction(RRWeb.MOUSE_UP, RRWeb.DOCUMENT_PADDING, RRWeb.DOCUMENT_PADDING, timestamp));
    return events;
  }

  // -- the custom events' payloads, built where they are recorded --

  /** A {@code Click}: what was clicked, its text when recorded, and the screen it was on. */
  static ReplayItem.Custom click(String target, String text, String id, String screenId, String screenName,
      long timestamp, String sessionId) {
    String payload = new JsonByteWriter().beginObject()
        .name("clickTarget").value(target != null ? target : "")
        .name("clickTextContent").value(text != null ? text : "")
        .name("clickSelector").value(id != null ? id : target != null ? target : CLICK_SELECTOR_FALLBACK)
        .optional("screenId", screenId)
        .optional("screenName", screenName)
        .endObject().toString();
    return new ReplayItem.Custom(RRWeb.TAG_CLICK, payload, timestamp, sessionId);
  }

  /** A {@code Navigate} to a screen; the payload is the screen's name as a plain string. */
  static ReplayItem.Custom navigate(String screenName, long timestamp, String sessionId) {
    return new ReplayItem.Custom(RRWeb.TAG_NAVIGATE, JsonByteWriter.quoted(screenName), timestamp, sessionId);
  }

  /** A {@code Foreground} or {@code Background}; the payload is stringified JSON, as on web and iOS. */
  static ReplayItem.Custom lifecycle(boolean foreground, long timestamp, String sessionId) {
    String state = new JsonByteWriter().beginObject()
        .name("lifecycle_state").value(foreground ? "foreground" : "background")
        .endObject().toString();
    return new ReplayItem.Custom(foreground ? RRWeb.TAG_FOREGROUND : RRWeb.TAG_BACKGROUND,
        JsonByteWriter.quoted(state), timestamp, sessionId);
  }

  /** A {@code Track}; the payload is stringified {@code {event, value, data}}, as on web. */
  static ReplayItem.Custom track(String key, Double value, String dataJson, long timestamp, String sessionId) {
    JsonByteWriter payload = new JsonByteWriter().beginObject().name("event").value(key);
    if (value != null) {
      payload.name("value").value(value.doubleValue());
    }
    payload.name("data").raw(dataJson).endObject();
    return new ReplayItem.Custom(RRWeb.TAG_TRACK, JsonByteWriter.quoted(payload.toString()), timestamp, sessionId);
  }

  /** A {@code Launch}; the payload is stringified JSON of the launch's fields. */
  static ReplayItem.Custom launch(String launchType, String version, String build, String previousVersion,
      long timestamp) {
    String payload = new JsonByteWriter().beginObject()
        .optional("launch_type", launchType)
        .optional("version", version)
        .optional("build", build)
        .optional("previous_version", previousVersion)
        .endObject().toString();
    return new ReplayItem.Custom(RRWeb.TAG_LAUNCH, JsonByteWriter.quoted(payload), timestamp, null);
  }
}
