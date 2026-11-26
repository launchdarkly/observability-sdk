package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.LDValue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The exporter against a recording backend, and the queue and worker that feed it. */
public class ExporterTest {
  /** Records each call, and fails the ones a test asks it to. */
  static class FakeApi extends ReplayApi {
    final List<String> calls = new ArrayList<>();
    final List<List<EventGenerator.Event>> pushes = new ArrayList<>();
    final List<String> payloadIds = new ArrayList<>();
    final List<ReplayItem.Identify> identifies = new ArrayList<>();
    // The next calls to fail, by operation, with what failure.
    final java.util.Map<String, ArrayList<ReplayApi.Failure>> failures = new java.util.HashMap<>();

    FakeApi() {
      super("http://unused", Collections.emptyMap(), new okhttp3.OkHttpClient()); // DevSkim: ignore DS137138
    }

    void failNext(String operation, boolean recoverable) {
      failures.computeIfAbsent(operation, o -> new ArrayList<>()).add(new ReplayApi.Failure(operation + " failed",
          recoverable, null));
    }

    private void maybeFail(String operation) throws ReplayApi.Failure {
      ArrayList<ReplayApi.Failure> list = failures.get(operation);
      if (list != null && !list.isEmpty()) {
        throw list.remove(0);
      }
    }

    @Override
    void initializeSession(String credential, String sessionId, String serviceName, String serviceVersion,
        String clientVersion) throws Failure {
      calls.add("init " + sessionId + " " + credential + " " + serviceName + " " + serviceVersion + " " + clientVersion);
      maybeFail("init");
    }

    @Override
    void identifySession(String sessionId, ReplayItem.Identify identify) throws Failure {
      calls.add("identify " + sessionId + " " + identify.userIdentifier());
      maybeFail("identify");
      identifies.add(identify);
    }

    @Override
    void pushPayload(String sessionId, int payloadId, List<EventGenerator.Event> events) throws Failure {
      calls.add("push " + sessionId + " " + payloadId + " " + events.size());
      maybeFail("push");
      pushes.add(events);
      payloadIds.add(sessionId + ":" + payloadId);
    }
  }

  private final FakeApi api = new FakeApi();
  private final List<String> refusals = new ArrayList<>();

  private ReplayExporter exporter(long canvasLimit) {
    return new ReplayExporter(api, "mob-key", "svc", "1.2", "0.1.0", "Demo",
        new ReplayItem.Identify(Collections.singletonMap("key", "anonymous"), 0, null), LDLogger.none(),
        refusals::add, canvasLimit);
  }

  private ReplayExporter exporter() {
    return exporter(ReplayExporter.CANVAS_LIMIT);
  }

  static ReplayItem frameItem(int keyFrameId, boolean keyframe, String session, int width, String data,
      long signature) {
    return new ReplayItem.Frame(new ExportFrame(keyFrameId, Collections.singletonList(
        new ExportFrame.AddImage(data, 0, 0, width, 10, EncodingTest.sig(signature))), Collections.emptyList(), width,
        10, 1000 + signature, keyframe, session));
  }

  private static int type(EventGenerator.Event event) {
    return LDValue.parse(event.json).get("type").intValue();
  }

  private static List<Integer> types(List<EventGenerator.Event> events) {
    List<Integer> types = new ArrayList<>();
    events.forEach(e -> types.add(type(e)));
    return types;
  }

  @Test
  public void preparingStartsTheSessionOnceAndReportsARefusal() {
    ReplayExporter exporter = exporter();
    api.failNext("init", true);
    assertFalse("not yet", exporter.prepare("s1"));
    assertTrue(exporter.prepare("s1"));
    assertTrue("already started", exporter.prepare("s1"));
    assertEquals(Arrays.asList("init s1 mob-key svc 1.2 0.1.0", "init s1 mob-key svc 1.2 0.1.0",
        "identify s1 anonymous"), api.calls);
    api.failNext("init", false);
    assertFalse(exporter.prepare("s2"));
    assertEquals(Collections.singletonList("init failed"), refusals);
    assertFalse("refused: nothing more is asked", exporter.prepare("s3"));
    assertEquals(4, api.calls.size());
  }

  @Test
  public void theFirstExportStartsTheSessionThenWakesThePlayer() throws Exception {
    ReplayExporter exporter = exporter();
    exporter.setLaunch(EventGenerator.launch("install", "1", null, null, 5));
    exporter.export(Arrays.asList(frameItem(1, true, "s1", 100, "AAA", 1), EventGenerator.navigate("Main", 1, null)));
    assertThat(api.calls, contains("init s1 mob-key svc 1.2 0.1.0", "identify s1 anonymous", "push s1 1 4",
        "push s1 2 4"));
    assertThat(types(api.pushes.get(0)), contains(4, 2, 5, 5));
    assertThat("reload, launch, then the click", types(api.pushes.get(1)), contains(5, 5, 3, 3));

    exporter.export(Collections.singletonList(frameItem(1, false, "s1", 100, "B", 2)));
    assertThat("incremental, and no second wake-up", api.calls.subList(4, api.calls.size()),
        contains("push s1 3 1"));
    assertThat(types(api.pushes.get(2)), contains(3));
  }

  @Test
  public void aNewSessionOrSizeStartsAFullSnapshot() throws Exception {
    ReplayExporter exporter = exporter();
    exporter.export(Collections.singletonList(frameItem(1, true, "s1", 100, "A", 1)));
    exporter.export(Collections.singletonList(frameItem(2, true, "s1", 120, "B", 2)));
    assertThat("a resize", types(api.pushes.get(2)), contains(4, 2, 5));
    exporter.export(Collections.singletonList(frameItem(3, true, "s2", 120, "C", 3)));
    assertThat(api.calls.subList(5, api.calls.size()), contains("init s2 mob-key svc 1.2 0.1.0",
        "identify s2 anonymous", "push s2 4 3", "push s2 5 3"));
    exporter.export(Collections.singletonList(new ReplayItem.Frame(new ExportFrame(4, Collections.singletonList(
        new ExportFrame.AddImage("D", 0, 0, 100, 10, EncodingTest.sig(4))), Collections.emptyList(), 120, 30, 5, true,
        "s2"))));
    assertEquals("a resize in the same session: no new start or wake-up", "push s2 6 3",
        api.calls.get(api.calls.size() - 1));
    assertEquals(10, api.calls.size());
  }

  @Test
  public void aFrameOnAnUnknownKeyframePushesNothing() throws Exception {
    ReplayExporter exporter = exporter();
    exporter.export(Collections.singletonList(frameItem(1, true, "s", 100, "A", 1)));
    int calls = api.calls.size();
    exporter.export(Collections.singletonList(frameItem(9, false, "s", 100, "B", 2)));
    assertEquals(calls, api.calls.size());
  }

  @Test
  public void tooMuchImageInThePlayerStartsAfreshOnAKeyframe() throws Exception {
    ReplayExporter exporter = exporter(10);
    exporter.export(Collections.singletonList(frameItem(1, true, "s", 100, "AAAA", 1)));
    exporter.export(Collections.singletonList(frameItem(1, false, "s", 100, "BBBB", 2)));
    assertThat("not on a non-keyframe", types(api.pushes.get(2)), contains(3));
    exporter.export(Collections.singletonList(frameItem(2, true, "s", 100, "CCCC", 3)));
    assertThat(types(api.pushes.get(3)), contains(4, 2, 5));
  }

  @Test
  public void eventsWithoutASessionJoinTheLastFrames() throws Exception {
    ReplayExporter exporter = exporter();
    exporter.export(Arrays.asList(EventGenerator.navigate("Early", 1, null),
        new ReplayItem.Pointer(ReplayItem.Pointer.Action.DOWN,
            Collections.singletonList(new ReplayItem.Position(1, 1, 1)), null)));
    assertThat("nothing to attach them to yet", api.calls, empty());
    exporter.export(Arrays.asList(frameItem(1, true, "s", 100, "A", 1),
        new ReplayItem.Pointer(ReplayItem.Pointer.Action.DOWN,
            Collections.singletonList(new ReplayItem.Position(1, 1, 1)), null),
        EventGenerator.click("JButton", null, null, null, null, 2, "s")));
    assertThat(types(api.pushes.get(0)), contains(4, 2, 5, 3, 5));
  }

  @Test
  public void aPushForASessionWithNoFrameStartsItFirst() throws Exception {
    ReplayExporter exporter = exporter();
    exporter.export(Collections.singletonList(EventGenerator.navigate("Main", 1, "s")));
    assertThat("started, pushed, but no player to wake without a frame", api.calls,
        contains("init s mob-key svc 1.2 0.1.0", "identify s anonymous", "push s 1 1"));
  }

  @Test
  public void anExportThatThrowsUncheckedRetriesTheSameEventsToo() throws Exception {
    FakeApi throwing = new FakeApi() {
      private boolean thrown;

      @Override
      void pushPayload(String sessionId, int payloadId, List<EventGenerator.Event> events) throws Failure {
        if (!thrown) {
          thrown = true;
          calls.add("push " + sessionId + " " + payloadId + " " + events.size());
          throw new IllegalStateException("network layer bug");
        }
        super.pushPayload(sessionId, payloadId, events);
      }
    };
    ReplayExporter exporter = new ReplayExporter(throwing, "mob-key", "svc", "1.2", "0.1.0", "Demo",
        new ReplayItem.Identify(Collections.singletonMap("key", "anonymous"), 0, null), LDLogger.none(),
        refusals::add, ReplayExporter.CANVAS_LIMIT);
    List<ReplayItem> items = Arrays.asList(frameItem(1, true, "s", 100, "A", 1),
        new ReplayItem.Pointer(ReplayItem.Pointer.Action.UP, Collections.singletonList(new ReplayItem.Position(1, 1, 3)),
            null));
    try {
      exporter.export(items);
      fail("expected the exception to reach the worker");
    } catch (IllegalStateException e) {
      assertEquals("network layer bug", e.getMessage());
    }
    exporter.export(items);
    assertEquals("the retry reuses payload id 1", "s:1", throwing.payloadIds.get(0));
    assertEquals("the failed push and its retry were both payload 1", 2,
        throwing.calls.stream().filter(c -> c.startsWith("push s 1 ")).count());
    String retried = throwing.pushes.get(0).get(0).json;
    assertEquals("the same sequence ids", 1, LDValue.parse(retried).get("_sid").intValue());
    assertTrue("a full snapshot again, not an incremental one",
        types(throwing.pushes.get(0)).contains(RRWeb.FULL_SNAPSHOT));
    assertFalse(exporter.isRefused());
  }

  @Test
  public void aFailedExportRetriesTheSameEvents() throws Exception {
    ReplayExporter exporter = exporter();
    api.failNext("push", true);
    List<ReplayItem> items = Arrays.asList(frameItem(1, true, "s", 100, "A", 1),
        new ReplayItem.Pointer(ReplayItem.Pointer.Action.UP, Collections.singletonList(new ReplayItem.Position(1, 1, 3)),
            null));
    try {
      exporter.export(items);
      fail("expected the failure to reach the worker");
    } catch (ReplayApi.Failure e) {
      assertTrue(e.recoverable);
    }
    exporter.export(items);
    assertThat(api.payloadIds, contains("s:1", "s:2"));
    String retried = api.pushes.get(0).get(0).json;
    assertEquals("the same sequence ids", 1, LDValue.parse(retried).get("_sid").intValue());
    assertFalse(exporter.isRefused());
  }

  @Test
  public void aFailedStartIsRetried() throws Exception {
    ReplayExporter exporter = exporter();
    api.failNext("identify", true);
    try {
      exporter.export(Collections.singletonList(frameItem(1, true, "s", 100, "A", 1)));
      fail("expected a failure");
    } catch (ReplayApi.Failure e) {
      // retried below
    }
    exporter.export(Collections.singletonList(frameItem(1, true, "s", 100, "A", 1)));
    assertEquals(2, api.calls.stream().filter(c -> c.startsWith("init")).count());
  }

  @Test
  public void aRefusalEndsReplayAndDrainsTheQueue() throws Exception {
    ReplayExporter exporter = exporter();
    api.failNext("init", false);
    exporter.export(Collections.singletonList(frameItem(1, true, "s", 100, "A", 1)));
    assertTrue(exporter.isRefused());
    assertThat(refusals, contains("init failed"));
    int calls = api.calls.size();
    exporter.export(Collections.singletonList(frameItem(2, true, "s", 100, "B", 2)));
    exporter.identify(new ReplayItem.Identify(Collections.singletonMap("key", "Jane"), 1, "s"));
    assertEquals("no more requests", calls, api.calls.size());
    exporter.export(Collections.emptyList());
  }

  /** An API whose push number {@code failAt} fails, recoverably or not. */
  private static FakeApi failingPush(int failAt, boolean recoverable) {
    return new FakeApi() {
      int pushes;

      @Override
      void pushPayload(String sessionId, int payloadId, List<EventGenerator.Event> events) throws Failure {
        if (++pushes == failAt) {
          calls.add("failed push " + sessionId + " " + payloadId);
          throw new Failure("push " + pushes + " failed", recoverable, null);
        }
        super.pushPayload(sessionId, payloadId, events);
      }
    };
  }

  private ReplayExporter exporterOn(FakeApi fake) {
    return new ReplayExporter(fake, "k", "svc", "1", "0", "T",
        new ReplayItem.Identify(Collections.singletonMap("key", "anonymous"), 0, null), LDLogger.none(),
        refusals::add);
  }

  @Test
  public void aRefusedWakeUpEndsReplayAndDropsTheRestOfTheBatch() throws Exception {
    FakeApi fake = failingPush(2, false); // t1's frame goes, its wake-up is refused
    ReplayExporter exporter = exporterOn(fake);
    exporter.export(Arrays.asList(frameItem(1, true, "t1", 100, "A", 1), frameItem(2, true, "t2", 100, "B", 2)));
    assertTrue(exporter.isRefused());
    assertThat(refusals, contains("push 2 failed"));
    assertTrue("t2 is never started", fake.calls.stream().noneMatch(c -> c.contains("t2")));
  }

  @Test
  public void aWakeUpThatFailsIsTriedAgainOnTheNextPush() throws Exception {
    FakeApi fake = failingPush(2, true);
    ReplayExporter exporter = exporterOn(fake);
    exporter.export(Collections.singletonList(frameItem(1, true, "s", 100, "A", 1)));
    exporter.export(Collections.singletonList(frameItem(1, false, "s", 100, "B", 2)));
    assertThat(fake.calls.subList(2, fake.calls.size()), contains("push s 1 3", "failed push s 2", "push s 3 1",
        "push s 4 3"));
    assertFalse(exporter.isRefused());
  }

  @Test
  public void anIdentifyWaitsForItsSessionToStart() throws Exception {
    ReplayExporter exporter = exporter();
    exporter.identify(new ReplayItem.Identify(Collections.singletonMap("key", "Jane"), 1, null));
    exporter.identify(new ReplayItem.Identify(Collections.singletonMap("key", "Jane"), 1, "s"));
    assertThat("held until the session starts", api.calls, empty());
    exporter.export(Collections.singletonList(frameItem(1, true, "s", 100, "A", 1)));
    assertEquals("identify s Jane", api.calls.get(1));

    exporter.identify(new ReplayItem.Identify(Collections.singletonMap("key", "Joe"), 1, "s"));
    assertEquals("identify s Joe", api.calls.get(api.calls.size() - 1));

    api.failNext("identify", true);
    exporter.identify(new ReplayItem.Identify(Collections.singletonMap("key", "Ann"), 1, "s"));
    assertFalse(exporter.isRefused());
    exporter.export(Collections.singletonList(frameItem(2, true, "s2", 100, "B", 2)));
    assertEquals("the last identify that went through", "identify s2 Joe", api.calls.get(api.calls.size() - 3));

    api.failNext("identify", false);
    exporter.identify(new ReplayItem.Identify(Collections.singletonMap("key", "Ann"), 1, "s"));
    assertTrue(exporter.isRefused());
  }

  // -- the queue --

  @Test
  public void theQueueKeepsWithinItsLimit() {
    ReplayQueue queue = new ReplayQueue(10);
    assertTrue("an empty queue takes anything", queue.offer(frameItem(1, true, "s", 1, "123456789012", 1)));
    assertTrue(queue.isFull());
    assertFalse(queue.offer(frameItem(1, true, "s", 1, "1", 2)));
    queue.removeFirst(5);
    assertEquals(0, queue.size());
    assertTrue(queue.offer(frameItem(1, true, "s", 1, "123", 1)));
    assertTrue(queue.offer(frameItem(1, true, "s", 1, "1234567", 2)));
    assertFalse(queue.offer(frameItem(1, true, "s", 1, "1", 3)));
    queue.clear();
    assertEquals(0, queue.size());
    assertFalse(queue.isFull());
    assertEquals(ReplayQueue.DEFAULT_COST_LIMIT, 2_500_000);
    assertTrue(new ReplayQueue().offer(frameItem(1, true, "s", 1, "1", 3)));
  }

  @Test
  public void aBatchStopsAtItsSizeOrJustPastItsBudget() {
    ReplayQueue queue = new ReplayQueue(1000);
    for (int i = 0; i < 5; i++) {
      queue.offer(frameItem(1, true, "s", 1, "1234567890", i));
    }
    assertEquals(3, queue.peek(25, 100).size());
    assertEquals(2, queue.peek(1000, 2).size());
    assertEquals(5, queue.peek(1000, 100).size());
  }

  // -- the worker --

  @Test
  public void theWorkerSendsInBatchesAndBacksOffAfterAFailure() {
    ReplayQueue queue = new ReplayQueue();
    List<Integer> sent = new ArrayList<>();
    AtomicReference<Boolean> fail = new AtomicReference<>(false);
    AtomicLong clock = new AtomicLong(1000);
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    BatchWorker worker = new BatchWorker(queue, items -> {
      if (fail.get()) {
        throw new ReplayApi.Failure("down", true, null);
      }
      sent.add(items.size());
    }, LDLogger.none(), executor, clock::get, new Random(1)); // DevSkim: ignore DS148264
    for (int i = 0; i < 150; i++) { // 300 each, so 100 fit the budget exactly
      queue.offer(new ReplayItem.Pointer(ReplayItem.Pointer.Action.DOWN,
          Collections.singletonList(new ReplayItem.Position(0, 0, i)), "s"));
    }
    worker.send();
    assertThat(sent, contains(100, 50));

    fail.set(true);
    queue.offer(EventGenerator.navigate("x", 1, "s"));
    worker.send();
    worker.send();
    assertEquals("backing off", 1, queue.size());
    clock.addAndGet(BatchWorker.BASE_BACKOFF_MILLIS * 2);
    worker.send(); // the second failure doubles the wait
    clock.addAndGet(BatchWorker.BASE_BACKOFF_MILLIS * 2);
    worker.send();
    assertEquals(1, queue.size());
    fail.set(false);
    clock.addAndGet(BatchWorker.MAX_BACKOFF_MILLIS * 2);
    worker.send();
    assertEquals(0, queue.size());
    executor.shutdownNow();
  }

  @Test
  public void aWorkerThatThrowsIsBackedOffToo() {
    ReplayQueue queue = new ReplayQueue();
    queue.offer(EventGenerator.navigate("x", 1, "s"));
    AtomicLong clock = new AtomicLong(0);
    BatchWorker worker = new BatchWorker(queue, items -> {
      throw new IllegalStateException("bug");
    }, LDLogger.none(), Executors.newSingleThreadScheduledExecutor(), clock::get, new Random(1)); // DevSkim: ignore DS148264
    worker.send();
    assertEquals(1, queue.size());
    worker.stop(1000);
  }

  @Test
  public void stoppingSendsWhatIsQueued() {
    ReplayQueue queue = new ReplayQueue();
    List<Integer> sent = Collections.synchronizedList(new ArrayList<>());
    BatchWorker worker = new BatchWorker(queue, items -> sent.add(items.size()), LDLogger.none());
    worker.start();
    queue.offer(EventGenerator.navigate("x", 1, "s"));
    worker.flush();
    queue.offer(EventGenerator.navigate("y", 1, "s"));
    assertTrue(worker.stop(5000));
    assertEquals(2, sent.stream().mapToInt(Integer::intValue).sum());
    worker.flush(); // stopped: ignored
  }

  @Test
  public void stoppingGivesUpOnABackendThatHangs() throws Exception {
    ReplayQueue queue = new ReplayQueue();
    queue.offer(EventGenerator.navigate("x", 1, "s"));
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    BatchWorker worker = new BatchWorker(queue, items -> {
      try {
        release.await();
      } catch (InterruptedException e) {
        throw new ReplayApi.Failure("interrupted", true, e);
      }
    }, LDLogger.none());
    assertFalse(worker.stop(200));
    release.countDown();
  }

  @Test
  public void stoppingWhenInterruptedStopsAtOnce() {
    ReplayQueue queue = new ReplayQueue();
    queue.offer(EventGenerator.navigate("x", 1, "s"));
    BatchWorker worker = new BatchWorker(queue, items -> {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        throw new ReplayApi.Failure("interrupted", true, e);
      }
    }, LDLogger.none());
    Thread.currentThread().interrupt();
    assertFalse(worker.stop(5000));
    assertTrue(Thread.interrupted());
  }
}
