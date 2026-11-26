package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Sends queued replay items to the backend, as the Android plugin's {@code SessionReplayExporter}
 * does: each session is started, and told who it belongs to, before its first push; a session's
 * first frame, a resize, or too much image data in the player sends a full snapshot; after a
 * session's first push, a second one wakes the player up.
 * <p>
 * A failed export leaves everything as it was before, so the retry sends the same events under the
 * same ids. A refusal the backend will not take back ends replay for the rest of the process: the
 * exporter makes no more requests, drops what it is given, and tells {@code onRefused}.
 * <p>
 * Thread-safe: exports and identifies run one at a time.
 */
final class ReplayExporter {
  /** Image data the player may hold before a full snapshot starts it afresh, as on Android. */
  static final long CANVAS_LIMIT = 10_000_000;
  /** The player's cost of drawing a tile, on top of its image data, as on Android. */
  static final int DRAW_COST = 300;

  private final ReplayApi api;
  private final String credential;
  private final String serviceName;
  private final String serviceVersion;
  private final String clientVersion;
  private final LDLogger logger;
  private final Consumer<String> onRefused;
  private final long canvasLimit;
  private final EventGenerator generator;

  private ReplayItem.Identify identify;
  private ReplayItem.Custom launch;
  private int payloadId;
  private String lastSession;
  private int lastWidth;
  private int lastHeight;
  private long pushedCanvasSize;
  private final Set<String> initializedSessions = new HashSet<>();
  private final Set<String> wokenSessions = new HashSet<>();
  private volatile boolean refused;

  ReplayExporter(ReplayApi api, String credential, String serviceName, String serviceVersion, String clientVersion,
      String title, ReplayItem.Identify identify, LDLogger logger, Consumer<String> onRefused) {
    this(api, credential, serviceName, serviceVersion, clientVersion, title, identify, logger, onRefused,
        CANVAS_LIMIT);
  }

  ReplayExporter(ReplayApi api, String credential, String serviceName, String serviceVersion, String clientVersion,
      String title, ReplayItem.Identify identify, LDLogger logger, Consumer<String> onRefused, long canvasLimit) {
    this.api = api;
    this.credential = credential;
    this.serviceName = serviceName;
    this.serviceVersion = serviceVersion;
    this.clientVersion = clientVersion;
    this.logger = logger;
    this.onRefused = onRefused;
    this.canvasLimit = canvasLimit;
    this.identify = identify;
    this.generator = new EventGenerator(title, DRAW_COST);
  }

  /** Whether the backend refused replay, which ends it for this process. */
  boolean isRefused() {
    return refused;
  }

  /** The launch to report in the first wake-up, which is when the session is sure to be ready for it. */
  synchronized void setLaunch(ReplayItem.Custom launch) {
    this.launch = launch;
  }

  /**
   * Sends the items, or throws so the caller retries them. After a refusal, returns without sending,
   * so the caller's queue drains.
   */
  synchronized void export(List<ReplayItem> items) throws ReplayApi.Failure {
    if (items.isEmpty() || refused) {
      return;
    }
    String lastSessionSnapshot = lastSession;
    int widthSnapshot = lastWidth;
    int heightSnapshot = lastHeight;
    int payloadIdSnapshot = payloadId;
    long pushedSnapshot = pushedCanvasSize;
    Set<String> wokenSnapshot = new HashSet<>(wokenSessions);
    ReplayItem.Custom launchSnapshot = launch;
    EventGenerator.State generatorSnapshot = generator.getState();
    try {
      generator.canvasSize = pushedCanvasSize;
      Map<String, List<EventGenerator.Event>> bySession = new LinkedHashMap<>();
      for (ReplayItem item : items) {
        if (item instanceof ReplayItem.Frame) {
          ExportFrame frame = ((ReplayItem.Frame) item).frame;
          events(bySession, frame.sessionId).addAll(frameEvents(frame));
          continue;
        }
        String session = item.sessionId != null ? item.sessionId : lastSession;
        if (session == null) {
          continue; // nothing to attach it to before the first frame
        }
        if (item instanceof ReplayItem.Pointer) {
          events(bySession, session).addAll(generator.pointer((ReplayItem.Pointer) item));
        } else {
          events(bySession, session).add(generator.custom((ReplayItem.Custom) item));
        }
      }
      for (Map.Entry<String, List<EventGenerator.Event>> entry : bySession.entrySet()) {
        if (entry.getValue().isEmpty()) {
          continue;
        }
        // A refusal can land part-way through, from a wake-up push; the rest is dropped.
        if (refused) {
          return;
        }
        String session = entry.getKey();
        initializeIfNeeded(session);
        api.pushPayload(session, ++payloadId, entry.getValue());
        logger.debug("Session replay sent {} events for session {}", entry.getValue().size(), session);
        pushedCanvasSize = generator.canvasSize;
        wakeUp(session, entry.getValue());
      }
    } catch (ReplayApi.Failure | RuntimeException e) {
      // The worker retries the batch after either, so put back what encoding it moved on: a retry
      // must send the same nodes under the same payload id, or the backend keeps both copies.
      lastSession = lastSessionSnapshot;
      lastWidth = widthSnapshot;
      lastHeight = heightSnapshot;
      payloadId = payloadIdSnapshot;
      pushedCanvasSize = pushedSnapshot;
      wokenSessions.clear();
      wokenSessions.addAll(wokenSnapshot);
      launch = launchSnapshot;
      generator.restoreState(generatorSnapshot);
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      ReplayApi.Failure failure = (ReplayApi.Failure) e;
      refuseIfUnrecoverable(failure);
      if (!refused) {
        throw failure;
      }
      // A refusal is not something to retry; returning lets the queue drain.
    }
  }

  private static List<EventGenerator.Event> events(Map<String, List<EventGenerator.Event>> bySession,
      String session) {
    return bySession.computeIfAbsent(session, s -> new ArrayList<>());
  }

  private List<EventGenerator.Event> frameEvents(ExportFrame frame) {
    boolean changed = !frame.sessionId.equals(lastSession) || frame.width != lastWidth
        || frame.height != lastHeight;
    boolean tooMuchImage = generator.canvasSize >= canvasLimit && frame.keyframe;
    if (changed || tooMuchImage) {
      lastSession = frame.sessionId;
      lastWidth = frame.width;
      lastHeight = frame.height;
      return generator.fullSnapshot(frame);
    }
    return generator.incremental(frame);
  }

  /**
   * Starts the session on the backend ahead of its first push, so that a refusal is known before
   * anything is pushed, as on Android. A failure that can be retried is only logged: the first push
   * tries again.
   *
   * @return whether the backend has started the session
   */
  synchronized boolean prepare(String session) {
    if (refused) {
      return false;
    }
    try {
      initializeIfNeeded(session);
      return true;
    } catch (ReplayApi.Failure e) {
      logger.debug("Session replay could not start the session yet: {}", e.getMessage());
      refuseIfUnrecoverable(e);
      return false;
    }
  }

  /** Starts the session and says who it belongs to, once. A failure leaves it to start next time. */
  private void initializeIfNeeded(String session) throws ReplayApi.Failure {
    if (!initializedSessions.add(session)) {
      return;
    }
    try {
      api.initializeSession(credential, session, serviceName, serviceVersion, clientVersion);
      api.identifySession(session, identify);
    } catch (ReplayApi.Failure e) {
      initializedSessions.remove(session);
      throw e;
    }
  }

  /** A session's first push is followed by one that starts the player. Its failure is only logged. */
  private void wakeUp(String session, List<EventGenerator.Event> pushed) {
    if (wokenSessions.contains(session)) {
      return;
    }
    List<EventGenerator.Event> events = generator.wakeUp(pushed.get(pushed.size() - 1).timestamp, launch);
    if (events.isEmpty()) {
      return;
    }
    try {
      api.pushPayload(session, ++payloadId, events);
      wokenSessions.add(session);
      launch = null; // reported once, in the first session
    } catch (ReplayApi.Failure e) {
      logger.warn("Session replay could not start the player for a session: {}", e.getMessage());
      refuseIfUnrecoverable(e);
    }
  }

  /**
   * Says who the session belongs to now. Sent at once to a session the backend has started; before
   * that, kept for when it starts, since the backend refuses identifying a session it does not know.
   */
  synchronized void identify(ReplayItem.Identify identify) {
    if (refused) {
      return;
    }
    if (identify.sessionId == null || !initializedSessions.contains(identify.sessionId)) {
      this.identify = identify;
      return;
    }
    try {
      api.identifySession(identify.sessionId, identify);
      this.identify = identify;
    } catch (ReplayApi.Failure e) {
      // There is nothing to retry an identify with, so it is only logged, unless it is a refusal.
      logger.warn("Session replay could not identify the session: {}", e.getMessage());
      refuseIfUnrecoverable(e);
    }
  }

  private void refuseIfUnrecoverable(ReplayApi.Failure failure) {
    if (refused || failure.recoverable) {
      return;
    }
    refused = true;
    logger.error("Session replay stopped: the backend refused it. {}", failure.getMessage());
    onRefused.accept(failure.getMessage());
  }
}
