package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.sdk.LDValue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The JSON writer and the rrweb events made with it. */
public class EncodingTest {
  static ExportFrame.Signature sig(long n) {
    return new ExportFrame.Signature(n, n);
  }

  static ExportFrame.AddImage tile(String data, int left, int top, long signature) {
    return new ExportFrame.AddImage(data, left, top, 10, 20, sig(signature));
  }

  static ExportFrame frame(int keyFrameId, boolean keyframe, List<ExportFrame.AddImage> adds,
      List<ExportFrame.Signature> removes, String session) {
    return new ExportFrame(keyFrameId, adds, removes, 640, 400, 1000 + keyFrameId, keyframe, session);
  }

  static LDValue json(EventGenerator.Event event) {
    return LDValue.parse(event.json);
  }

  // -- the writer --

  @Test
  public void writesNestedValuesWithCommas() {
    String json = new JsonWriter().beginObject()
        .name("a").value(1)
        .name("b").beginArray().value("x").value(true).value((String) null).raw("{\"r\":1}").endArray()
        .name("c").beginObject().endObject()
        .optional("skipped", null)
        .optional("kept", "k")
        .endObject().toString();
    assertEquals("{\"a\":1,\"b\":[\"x\",true,null,{\"r\":1}],\"c\":{},\"kept\":\"k\"}", json);
  }

  @Test
  public void escapesWhatJsonNeedsEscaped() {
    String s = "q\" b\\ n\n r\r t\t c\u0001 l  p  é";
    String json = new JsonWriter().value(s).toString();
    assertEquals(s, LDValue.parse(json).stringValue());
    assertEquals("\"q\\\" b\\\\ n\\n r\\r t\\t c\\u0001 l\\u2028 p\\u2029 é\"", json);
    assertEquals("\"plain\"", JsonWriter.quoted("plain"));
  }

  @Test
  public void writesNumbers() {
    assertEquals("[3,2.5,null,null,-7,1.0E20]", new JsonWriter().beginArray().value(3.0).value(2.5)
        .value(Double.NaN).value(Double.POSITIVE_INFINITY).value(-7L).value(1e20).endArray().toString());
  }

  @Test
  public void nestsDeeperThanItsFirstGuess() {
    JsonWriter w = new JsonWriter();
    for (int i = 0; i < 40; i++) {
      w.beginArray().value(i);
    }
    for (int i = 0; i < 40; i++) {
      w.endArray();
    }
    assertEquals(40, depth(LDValue.parse(w.toString())));
  }

  private static int depth(LDValue v) {
    return v.size() > 1 ? 1 + depth(v.get(1)) : 1;
  }

  @Test
  public void writesAStringMapInOrder() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("b", "2");
    map.put("a", "1");
    assertEquals("{\"b\":\"2\",\"a\":\"1\"}", new JsonWriter().stringMap(map).toString());
  }

  // -- frames --

  @Test
  public void aFullSnapshotIsMetaDocumentAndViewport() {
    EventGenerator g = new EventGenerator("App", 300);
    List<EventGenerator.Event> events = g.fullSnapshot(frame(1, true,
        Arrays.asList(tile("AAA", 0, 0, 1), tile("BBB", 10, 20, 2)), Collections.emptyList(), "s"));
    assertEquals(3, events.size());

    LDValue meta = json(events.get(0));
    assertEquals(RRWeb.META, meta.get("type").intValue());
    assertEquals(662, meta.get("data").get("width").intValue());
    assertEquals(422, meta.get("data").get("height").intValue());
    assertEquals(1, meta.get("_sid").intValue());
    assertEquals(1001, meta.get("timestamp").longValue());

    LDValue doc = json(events.get(1)).get("data").get("node");
    assertEquals(RRWeb.DOCUMENT_NODE, doc.get("type").intValue());
    LDValue html = doc.get("childNodes").get(0);
    assertEquals("html", html.get("tagName").stringValue());
    assertEquals("en", html.get("attributes").get("lang").stringValue());
    assertEquals("head", html.get("childNodes").get(0).get("tagName").stringValue());
    LDValue body = html.get("childNodes").get(1);
    assertEquals("position:relative;", body.get("attributes").get("style").stringValue());
    LDValue second = body.get("childNodes").get(1);
    assertEquals("img", second.get("tagName").stringValue());
    assertEquals("data:image/jpeg;base64,BBB", second.get("attributes").get("src").stringValue());
    assertEquals("10", second.get("attributes").get("width").stringValue());
    assertEquals("position:absolute;left:10px;top:20px;pointer-events:none;",
        second.get("attributes").get("style").stringValue());
    // Node ids as on Android: document 1, head 2, body 3, tiles 4 and 5, html 6.
    assertEquals(1, doc.get("id").intValue());
    assertEquals(6, html.get("id").intValue());
    assertEquals(3, body.get("id").intValue());
    assertEquals(5, second.get("id").intValue());

    LDValue viewport = json(events.get(2)).get("data");
    assertEquals(RRWeb.TAG_VIEWPORT, viewport.get("tag").stringValue());
    assertEquals(640, viewport.get("payload").get("width").intValue());
    assertEquals(30, viewport.get("payload").get("colorDepth").intValue());
    assertEquals(6 + 300, g.canvasSize);
  }

  @Test
  public void aFullSnapshotOfNothingIsNothing() {
    assertTrue(new EventGenerator("App", 0).fullSnapshot(frame(1, true, Collections.emptyList(),
        Collections.emptyList(), "s")).isEmpty());
  }

  @Test
  public void anIncrementalFrameAddsAndRemovesTiles() {
    EventGenerator g = new EventGenerator("App", 300);
    g.fullSnapshot(frame(1, true, Collections.singletonList(tile("AAA", 0, 0, 1)), Collections.emptyList(), "s"));
    List<EventGenerator.Event> events = g.incremental(frame(1, false, Collections.singletonList(tile("CC", 5, 6, 3)),
        Arrays.asList(sig(1), sig(99)), "s"));
    LDValue data = json(events.get(0)).get("data");
    assertEquals(RRWeb.SOURCE_MUTATION, data.get("source").intValue());
    assertEquals("only known tiles are removed", 1, data.get("removes").size());
    assertEquals(4, data.get("removes").get(0).get("id").intValue());
    assertEquals(3, data.get("removes").get(0).get("parentId").intValue());
    LDValue add = data.get("adds").get(0);
    assertEquals(3, add.get("parentId").intValue());
    assertEquals("after html, node 5", 6, add.get("node").get("id").intValue());
    assertEquals("data:image/jpeg;base64,CC", add.get("node").get("attributes").get("src").stringValue());
    assertEquals(3 + 300 + 2 + 300, g.canvasSize);
  }

  @Test
  public void aFrameOnAnUnknownKeyframeIsDropped() {
    EventGenerator g = new EventGenerator("App", 0);
    assertTrue("no snapshot yet", g.incremental(frame(1, false, Collections.singletonList(tile("A", 0, 0, 1)),
        Collections.emptyList(), "s")).isEmpty());
    g.fullSnapshot(frame(1, true, Collections.singletonList(tile("A", 0, 0, 1)), Collections.emptyList(), "s"));
    assertTrue(g.incremental(frame(2, false, Collections.singletonList(tile("B", 0, 0, 2)),
        Collections.emptyList(), "s")).isEmpty());
  }

  @Test
  public void aKeyframeForgetsEarlierTilesAndMovesThePointerTarget() {
    EventGenerator g = new EventGenerator("App", 0);
    g.fullSnapshot(frame(1, true, Collections.singletonList(tile("A", 0, 0, 1)), Collections.emptyList(), "s"));
    LDValue key = json(g.incremental(frame(2, true, Arrays.asList(tile("K", 0, 0, 5), tile("L", 0, 0, 6)),
        Collections.singletonList(sig(1)), "s")).get(0)).get("data");
    assertEquals("the removal still names the old tile", 1, key.get("removes").size());
    LDValue after = json(g.incremental(frame(2, false, Collections.emptyList(), Collections.singletonList(sig(1)),
        "s")).get(0)).get("data");
    assertEquals("the keyframe forgot it", 0, after.get("removes").size());
    LDValue down = json(g.pointer(new ReplayItem.Pointer(ReplayItem.Pointer.Action.DOWN,
        Collections.singletonList(new ReplayItem.Position(1, 2, 5)), "s")).get(0)).get("data");
    assertEquals("the keyframe's first tile", 6, down.get("id").intValue());
    // A keyframe with no tiles keeps the target.
    g.incremental(frame(3, true, Collections.emptyList(), Collections.emptyList(), "s"));
    assertEquals(6, json(g.wakeUp(1, null).get(1)).get("data").get("id").intValue());
  }

  @Test
  public void aTileWithoutASignatureIsNeverRemoved() {
    EventGenerator g = new EventGenerator("App", 0);
    g.fullSnapshot(frame(1, true, Collections.singletonList(new ExportFrame.AddImage("A", 0, 0, 1, 1, null)),
        Collections.emptyList(), "s"));
    assertEquals(0, json(g.incremental(frame(1, false, Collections.emptyList(), Collections.singletonList(sig(0)),
        "s")).get(0)).get("data").get("removes").size());
  }

  @Test
  public void stateRestoresExactly() {
    EventGenerator g = new EventGenerator("App", 0);
    g.fullSnapshot(frame(1, true, Collections.singletonList(tile("A", 0, 0, 1)), Collections.emptyList(), "s"));
    EventGenerator.State saved = g.getState();
    String first = g.incremental(frame(1, false, Collections.singletonList(tile("B", 0, 0, 2)),
        Collections.singletonList(sig(1)), "s")).get(0).json;
    g.restoreState(saved);
    String again = g.incremental(frame(1, false, Collections.singletonList(tile("B", 0, 0, 2)),
        Collections.singletonList(sig(1)), "s")).get(0).json;
    assertEquals(first, again);
  }

  // -- the pointer --

  @Test
  public void thePointerIsMouseEventsShiftedByThePadding() {
    EventGenerator g = new EventGenerator("App", 0);
    LDValue before = json(g.pointer(new ReplayItem.Pointer(ReplayItem.Pointer.Action.UP,
        Arrays.asList(new ReplayItem.Position(0, 0, 1), new ReplayItem.Position(7, 8, 2)), "s")).get(0));
    assertEquals(RRWeb.MOUSE_UP, before.get("data").get("type").intValue());
    assertEquals(18, before.get("data").get("x").intValue());
    assertEquals(2, before.get("timestamp").longValue());
    assertTrue("no image yet", before.get("data").get("id").isNull());

    g.fullSnapshot(frame(1, true, Collections.singletonList(tile("A", 0, 0, 1)), Collections.emptyList(), "s"));
    List<EventGenerator.Event> moves = g.pointer(new ReplayItem.Pointer(ReplayItem.Pointer.Action.MOVE,
        Arrays.asList(new ReplayItem.Position(1, 2, 10), new ReplayItem.Position(3, 4, 20)), "s"));
    assertEquals(2, moves.size());
    LDValue move = json(moves.get(1)).get("data");
    assertEquals(RRWeb.SOURCE_MOUSE_MOVE, move.get("source").intValue());
    LDValue position = move.get("positions").get(0);
    assertEquals(4, position.get("id").intValue());
    assertEquals(14, position.get("x").intValue());
    assertEquals(15, position.get("y").intValue());
    assertTrue(json(g.pointer(new ReplayItem.Pointer(ReplayItem.Pointer.Action.MOVE,
        Collections.singletonList(new ReplayItem.Position(1, 1, 1)), "s")).get(0)).get("data").get("positions")
        .get(0).get("id").isNumber());

    EventGenerator fresh = new EventGenerator("App", 0);
    assertTrue(json(fresh.pointer(new ReplayItem.Pointer(ReplayItem.Pointer.Action.MOVE,
        Collections.singletonList(new ReplayItem.Position(1, 1, 1)), "s")).get(0)).get("data").get("positions")
        .get(0).get("id").isNull());
  }

  // -- custom events --

  @Test
  public void customEventsCarryTheirPayloads() {
    EventGenerator g = new EventGenerator("App", 0);
    LDValue click = json(g.custom(EventGenerator.click("JButton", "Save", "save", "main", "Main", 5, "s")))
        .get("data");
    assertEquals(RRWeb.TAG_CLICK, click.get("tag").stringValue());
    assertEquals("JButton", click.get("payload").get("clickTarget").stringValue());
    assertEquals("Save", click.get("payload").get("clickTextContent").stringValue());
    assertEquals("save", click.get("payload").get("clickSelector").stringValue());
    assertEquals("Main", click.get("payload").get("screenName").stringValue());

    LDValue bare = json(g.custom(EventGenerator.click("JPanel", null, null, null, null, 5, "s"))).get("data")
        .get("payload");
    assertEquals("", bare.get("clickTextContent").stringValue());
    assertEquals("JPanel", bare.get("clickSelector").stringValue());
    assertTrue(bare.get("screenId").isNull());
    assertEquals("view", json(g.custom(EventGenerator.click(null, null, null, null, null, 5, "s"))).get("data")
        .get("payload").get("clickSelector").stringValue());

    assertEquals("Settings", json(g.custom(EventGenerator.navigate("Settings", 1, "s"))).get("data").get("payload")
        .stringValue());

    LDValue fg = json(g.custom(EventGenerator.lifecycle(true, 1, "s"))).get("data");
    assertEquals(RRWeb.TAG_FOREGROUND, fg.get("tag").stringValue());
    assertEquals("foreground", LDValue.parse(fg.get("payload").stringValue()).get("lifecycle_state").stringValue());
    assertEquals(RRWeb.TAG_BACKGROUND, json(g.custom(EventGenerator.lifecycle(false, 1, "s"))).get("data").get("tag")
        .stringValue());

    LDValue track = LDValue.parse(json(g.custom(EventGenerator.track("bought", 2.5, "{\"item\":\"hat\"}", 1, "s")))
        .get("data").get("payload").stringValue());
    assertEquals("bought", track.get("event").stringValue());
    assertEquals(2.5, track.get("value").doubleValue(), 0);
    assertEquals("hat", track.get("data").get("item").stringValue());
    assertTrue(LDValue.parse(json(g.custom(EventGenerator.track("k", null, "{}", 1, "s"))).get("data").get("payload")
        .stringValue()).get("value").isNull());

    ReplayItem.Custom launch = EventGenerator.launch("relaunch", "1.2", null, null, 1);
    LDValue l = LDValue.parse(json(g.custom(launch)).get("data").get("payload").stringValue());
    assertEquals("relaunch", l.get("launch_type").stringValue());
    assertTrue(l.get("build").isNull());

    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("user", "u1");
    attributes.put("key", "Jane");
    ReplayItem.Identify identify = new ReplayItem.Identify(attributes, 1, "s");
    assertEquals("Jane", identify.userIdentifier());
    assertEquals(200, identify.cost());
    assertEquals("u1", LDValue.parse(json(g.custom(identify)).get("data").get("payload").stringValue()).get("user")
        .stringValue());
    assertEquals("unknown", new ReplayItem.Identify(Collections.emptyMap(), 1, null).userIdentifier());
  }

  @Test
  public void theWakeUpReloadsThenClicks() {
    EventGenerator g = new EventGenerator("Demo", 0);
    assertTrue("nothing to click before a frame", g.wakeUp(1, null).isEmpty());
    g.fullSnapshot(frame(1, true, Collections.singletonList(tile("A", 0, 0, 1)), Collections.emptyList(), "s"));
    List<EventGenerator.Event> events = g.wakeUp(9, EventGenerator.launch("install", "1", "2", "0", 9));
    assertEquals(4, events.size());
    assertEquals("Demo", json(events.get(0)).get("data").get("payload").stringValue());
    assertEquals(RRWeb.TAG_LAUNCH, json(events.get(1)).get("data").get("tag").stringValue());
    assertEquals(RRWeb.MOUSE_DOWN, json(events.get(2)).get("data").get("type").intValue());
    assertEquals(RRWeb.DOCUMENT_PADDING, json(events.get(3)).get("data").get("x").intValue());
    assertEquals(3, g.wakeUp(9, null).size());
  }

  @Test
  public void itemsCostRoughlyTheirSize() {
    assertEquals(300, new ReplayItem.Pointer(ReplayItem.Pointer.Action.DOWN,
        Collections.singletonList(new ReplayItem.Position(0, 0, 0)), null).cost());
    assertEquals(300 + 2, EventGenerator.navigate("", 0, null).cost());
    ExportFrame f = frame(1, true, Arrays.asList(tile("AAA", 0, 0, 1), tile("BB", 0, 0, 2)), Collections.emptyList(),
        "s");
    assertEquals(5, new ReplayItem.Frame(f).cost());
    assertEquals("s", new ReplayItem.Frame(f).sessionId);
  }

  @Test
  public void signaturesCompareByValue() {
    assertEquals(sig(3), new ExportFrame.Signature(3, 3));
    assertEquals(sig(3).hashCode(), new ExportFrame.Signature(3, 3).hashCode());
    assertFalse(sig(3).equals(new ExportFrame.Signature(3, 4)));
    assertFalse(sig(3).equals(new ExportFrame.Signature(4, 3)));
    assertFalse(sig(3).equals("3"));
  }
}
