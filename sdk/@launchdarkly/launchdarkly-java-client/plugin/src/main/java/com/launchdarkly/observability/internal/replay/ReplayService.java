package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.PrivacyProfile;
import com.launchdarkly.observability.ReplayOptions;
import com.launchdarkly.observability.SessionReplayStartResult;
import com.launchdarkly.observability.internal.AppLaunch;
import com.launchdarkly.observability.internal.Click;
import com.launchdarkly.observability.internal.ObservabilityListener;
import com.launchdarkly.observability.internal.ObservabilityService;
import com.launchdarkly.observability.internal.ToolkitSupport;
import com.launchdarkly.observability.internal.Version;

import io.opentelemetry.api.common.Attributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Session replay for one running observability service, as the Android plugin's
 * {@code SessionReplayService}: it takes frames of the active window, records the mouse, puts the
 * service's screens, clicks, tracks, identifies and foreground changes on the replay's timeline,
 * and sends it all in the service's session.
 * <p>
 * Recording is on while replay is enabled and this session was sampled in. Frames are taken only
 * while the application is in the foreground, and not at all after the backend has refused a
 * previous launch, until it accepts this one. A refusal stops replay until the next launch.
 * <p>
 * Thread-safe. Listener calls come from the UI threads and must return quickly, so nothing here
 * blocks on the network: requests run on the export thread.
 */
public final class ReplayService implements ObservabilityListener {
  static final long STOP_TIMEOUT_MILLIS = 3000;
  static final String FX_CAPTURE_CLASS = "com.launchdarkly.observability.internal.replay.FxCapture";
  static final String SWT_CAPTURE_CLASS = "com.launchdarkly.observability.internal.replay.SwtCapture";

  private final ReplayOptions options;
  private final String friendlyName;
  private final Map<String, String> resource;
  private final Supplier<String> sessions;
  private final LDLogger logger;
  private final ReplayApi api;
  private final RefusalStore refusals;
  private final DoubleSupplier random;
  private final ReplayQueue queue = new ReplayQueue();
  private final PointerRecorder pointer;
  private final ReplayExporter exporter;
  private final BatchWorker worker;
  private final CaptureLoop loop;
  private volatile Runnable onStopped = () -> { };

  private final Object lock = new Object();
  // Guarded by lock.
  private boolean begun;
  private boolean enabled;
  private boolean running;
  private boolean sampled; // whether sampling has decided for this enable
  private boolean foreground = true;
  private boolean withheld;
  private boolean refused;
  private boolean stopped;
  private ReplayItem.Identify identify;
  private boolean identifyPending;
  // Written on the UI threads, read with each click.
  private volatile String screenName;
  private volatile String screenId;

  /** The production service for a running observability service, not yet recording. */
  public static ReplayService create(ObservabilityService service, ReplayOptions options) {
    ObservabilityOptions o = service.getOptions();
    LDLogger logger = logger(o, service.getLogger(), options.isDebug());
    Map<String, String> resource = new LinkedHashMap<>();
    service.getResource().getAttributes().forEach((key, value) -> resource.put(key.getKey(), String.valueOf(value)));
    MaskRules rules = rules(options.getPrivacyProfile());
    return new ReplayService(service.getCredential(), o, options, resource, service.getAppLaunch(),
        service::getSessionId, logger, new ReplayApi(o.getBackendUrl(), o.getCustomHeaders()),
        sink -> sources(options.getScale(), rules, logger, sink),
        new RefusalStore.InPreferences(service.getCredential()), new Random()::nextDouble,
        daemonExecutor("LaunchDarkly-session-replay-capture"), daemonExecutor("LaunchDarkly-session-replay-export"));
  }

  ReplayService(String credential, ObservabilityOptions observability, ReplayOptions options,
      Map<String, String> resource, AppLaunch launch, Supplier<String> sessions, LDLogger logger, ReplayApi api,
      Function<PointerSink, List<CaptureSource>> sources, RefusalStore refusals, DoubleSupplier random,
      ScheduledExecutorService captureExecutor, ScheduledExecutorService exportExecutor) {
    this.options = options;
    this.friendlyName = observability.getContextFriendlyName();
    this.resource = resource;
    this.sessions = sessions;
    this.logger = logger;
    this.api = api;
    this.refusals = refusals;
    this.random = random;
    this.pointer = new PointerRecorder(queue, sessions);
    this.identify = identify(Collections.<String, String>emptyMap(), null, System.currentTimeMillis(), null);
    this.exporter = new ReplayExporter(api, credential, observability.getServiceName(),
        observability.getServiceVersion(), Version.VERSION, observability.getServiceName(), identify, logger,
        this::refused);
    if (launch != null) {
      exporter.setLaunch(EventGenerator.launch(launch.getLaunchType(), launch.getVersion(), launch.getBuild(),
          launch.getPreviousVersion(), launch.getTimestamp()));
    }
    this.worker = new BatchWorker(queue, exporter::export, logger, exportExecutor, System::currentTimeMillis,
        new Random());
    long interval = Math.max(1, Math.round(1000 / options.getFrameRate()));
    ReplayOptions.CompressionMethod compression = options.getCompression();
    this.loop = new CaptureLoop(sources.apply(pointer), new FrameEncoder((float) options.getImageQuality(),
        compression.isScreenImage(), compression.getLayers(), compression.isBacktracking()), queue,
        sessions, interval, logger, captureExecutor);
    loop.onFrame = pointer::frameTaken;
    loop.onTick = pointer::flush;
    loop.pause();
  }

  /** Replay's logger: the plugin's, or with {@code debug}, one that logs at debug level too. */
  static LDLogger logger(ObservabilityOptions observability, LDLogger plugin, boolean debug) {
    return debug
        ? LDLogger.withAdapter(Logs.level(observability.getLogAdapter(), LDLogLevel.DEBUG), observability.getLoggerName())
        : plugin;
  }

  /** The rules a privacy profile sets. */
  static MaskRules rules(PrivacyProfile profile) {
    return new MaskRules(profile.isMaskTextInputs(), profile.isMaskText(), profile.isMaskImages(),
        profile.isMaskWebViews(), profile.isMaskBySemanticsKeywords(), profile.getMinimumAlpha(),
        profile.getMaskNames(), profile.getUnmaskNames(), profile.getMaskClasses(), profile.getUnmaskClasses());
  }

  /** A capture source for each toolkit the runtime has. Neither toolkit is started. */
  static List<CaptureSource> sources(double scale, MaskRules rules, LDLogger logger, PointerSink sink) {
    List<CaptureSource> sources = new ArrayList<>();
    addSwingCapture(sources, scale, rules, logger, sink);
    addFxCapture(sources, "javafx.stage.Window", FX_CAPTURE_CLASS, scale, rules, logger, sink);
    addCapture(sources, "SWT", "org.eclipse.swt.widgets.Display", SWT_CAPTURE_CLASS, scale, rules, logger, sink);
    return sources;
  }

  static void addSwingCapture(List<CaptureSource> sources, double scale, MaskRules rules, LDLogger logger,
      PointerSink sink) {
    try {
      SwingCapture capture = new SwingCapture(scale, new SwingMasks(rules), logger, ToolkitSupport::awtInUse);
      capture.pointer = sink;
      sources.add(capture);
    } catch (LinkageError e) {
      logger.info("Session replay is not capturing Swing and AWT windows, since this runtime has no AWT: {}",
          e.toString());
    }
  }

  /** Adds the JavaFX capture if the runtime has JavaFX, loading it by name so nothing refers to JavaFX without it. */
  static void addFxCapture(List<CaptureSource> sources, String probeClass, String captureClass, double scale,
      MaskRules rules, LDLogger logger, PointerSink sink) {
    addCapture(sources, "JavaFX", probeClass, captureClass, scale, rules, logger, sink);
  }

  /** Adds a toolkit's capture if the runtime has the toolkit, loading it by name. */
  static void addCapture(List<CaptureSource> sources, String toolkit, String probeClass, String captureClass,
      double scale, MaskRules rules, LDLogger logger, PointerSink sink) {
    ClassLoader loader = ReplayService.class.getClassLoader();
    try {
      Class.forName(probeClass, false, loader);
    } catch (ClassNotFoundException | LinkageError e) {
      return; // no such toolkit
    }
    try {
      sources.add((CaptureSource) Class.forName(captureClass, true, loader)
          .getDeclaredMethod("create", double.class, MaskRules.class, LDLogger.class, PointerSink.class)
          .invoke(null, scale, rules, logger, sink));
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      logger.info("Session replay is not capturing {} windows: {}", toolkit, e.toString());
    }
  }

  private static ScheduledExecutorService daemonExecutor(String name) {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, name);
      thread.setDaemon(true);
      return thread;
    });
  }

  /** Called once replay has stopped for good, after a {@link #shutdown()} or the service's stop. */
  public void setOnStopped(Runnable onStopped) {
    this.onStopped = onStopped;
  }

  /**
   * Starts the threads, and recording if asked to. Called once.
   *
   * @param enable whether to start recording
   * @param ignoreSampling whether that start ignores sampling
   */
  public void begin(boolean enable, boolean ignoreSampling) {
    String cached = refusals.load();
    synchronized (lock) {
      if (begun) {
        return;
      }
      begun = true;
      withheld = cached != null;
    }
    if (cached != null) {
      logger.info("Session replay is taking no screenshots until the backend accepts this session; it refused"
          + " the last launch: {}", cached);
    }
    worker.start();
    loop.start();
    if (enable) {
      start(ignoreSampling);
    }
  }

  // -- control --

  /** Whether replay is enabled; a session sampled out is enabled but not recording. */
  public boolean isEnabled() {
    synchronized (lock) {
      return enabled;
    }
  }

  /** Whether replay is recording. */
  public boolean isRunning() {
    synchronized (lock) {
      return running;
    }
  }

  /** Enables or disables replay; enabling has sampling decide, unless it already has for this enable. */
  public void setEnabled(boolean enable) {
    if (enable) {
      if (!isEnabled()) {
        start(false);
      }
    } else {
      stop();
    }
  }

  /**
   * Enables replay and starts recording, unless sampling leaves this session out. Sampling decides
   * once each time replay is enabled; {@code ignoreSampling} records anyway.
   */
  public SessionReplayStartResult start(boolean ignoreSampling) {
    ReplayItem.Identify pending = null;
    synchronized (lock) {
      if (stopped || !begun) {
        return SessionReplayStartResult.UNAVAILABLE;
      }
      if (refused) {
        return SessionReplayStartResult.UNRECOVERABLE_ERROR;
      }
      enabled = true;
      if (running) {
        return SessionReplayStartResult.ALREADY_STARTED;
      }
      if (!ignoreSampling) {
        if (sampled) {
          return SessionReplayStartResult.SAMPLED_OUT;
        }
        sampled = true;
        if (!sample(options.getSampleRate())) {
          logger.info("Session replay is not recording this session: sampling left it out");
          return SessionReplayStartResult.SAMPLED_OUT;
        }
      }
      running = true;
      update();
      if (identifyPending) {
        identifyPending = false;
        pending = identify;
      }
    }
    String session = sessions.get();
    if (pending != null) {
      sendIdentify(new ReplayItem.Identify(pending.attributes, System.currentTimeMillis(), session));
    }
    String screen = screenName;
    if (screen != null) {
      queue.offer(EventGenerator.navigate(screen, System.currentTimeMillis(), session));
    }
    // Ask the backend at once, so that a refusal is known before anything else is sent.
    probe(session);
    return SessionReplayStartResult.STARTED;
  }

  /** Stops recording and disables replay. A later start has sampling decide again. */
  public void stop() {
    pointer.flush();
    synchronized (lock) {
      enabled = false;
      running = false;
      sampled = false;
      update();
    }
  }

  /** Sends what is queued now. */
  public void flush() {
    pointer.flush();
    worker.flush();
  }

  /** Stops recording for good, sending what is queued for a few seconds. */
  public void shutdown() {
    synchronized (lock) {
      if (stopped) {
        return;
      }
      stopped = true;
      enabled = false;
      running = false;
    }
    pointer.flush();
    pointer.setRecording(false);
    loop.stop();
    if (!worker.stop(STOP_TIMEOUT_MILLIS)) {
      logger.debug("Session replay stopped before sending everything queued");
    }
    api.close();
    onStopped.run();
  }

  boolean sample(double rate) {
    if (rate <= 0) {
      return false;
    }
    return rate >= 1 || random.getAsDouble() < rate;
  }

  /** Captures while recording in the foreground, unless screenshots are withheld. Holding the lock. */
  private void update() {
    if (running && foreground && !withheld) {
      loop.resume();
    } else {
      loop.pause();
    }
    pointer.setRecording(running && foreground);
  }

  // -- the backend's verdict --

  private void probe(String session) {
    worker.run(() -> {
      if (exporter.prepare(session)) {
        accepted();
      }
    });
  }

  private void accepted() {
    synchronized (lock) {
      if (!withheld) {
        return;
      }
      withheld = false;
      update();
    }
    refusals.clear();
    logger.info("Session replay: the backend accepted this session, so screenshots resume");
  }

  /** The exporter's refusal. On the export thread. */
  private void refused(String reason) {
    synchronized (lock) {
      refused = true;
      enabled = false;
      running = false;
      update();
    }
    refusals.store(reason);
    queue.clear();
  }

  // -- what the observability service records --

  @Override
  public void foreground() {
    boolean recording;
    boolean retry;
    synchronized (lock) {
      foreground = true;
      update();
      recording = running;
      retry = running && withheld;
    }
    if (recording) {
      String session = sessions.get();
      queue.offer(EventGenerator.lifecycle(true, System.currentTimeMillis(), session));
      if (retry) {
        // Screenshots are held until the backend answers, and coming back is the moment to ask again.
        probe(session);
      }
    }
  }

  @Override
  public void background() {
    pointer.flush();
    boolean recording;
    synchronized (lock) {
      foreground = false;
      update();
      recording = running;
    }
    if (recording) {
      queue.offer(EventGenerator.lifecycle(false, System.currentTimeMillis(), sessions.get()));
    }
    worker.flush();
  }

  @Override
  public void screenShown(String name, String screenClass, String screenId) {
    this.screenName = name;
    this.screenId = screenId;
    if (isRunning()) {
      queue.offer(EventGenerator.navigate(name, System.currentTimeMillis(), sessions.get()));
    }
  }

  @Override
  public void click(Click click) {
    if (isRunning()) {
      queue.offer(EventGenerator.click(click.getTag(), click.getText(), click.getId(), screenId, screenName,
          click.getEndMillis(), sessions.get()));
    }
  }

  @Override
  public void track(String key, Double metricValue, Attributes data) {
    if (isRunning()) {
      Map<String, String> flat = new LinkedHashMap<>();
      data.forEach((k, v) -> flat.put(k.getKey(), String.valueOf(v)));
      queue.offer(EventGenerator.track(key, metricValue, new JsonByteWriter().stringMap(flat).toString(),
          System.currentTimeMillis(), sessions.get()));
    }
  }

  @Override
  public void identified(Map<String, String> contextKeys, String canonicalKey) {
    ReplayItem.Identify next = identify(contextKeys, canonicalKey, System.currentTimeMillis(), sessions.get());
    boolean send;
    synchronized (lock) {
      identify = next;
      send = running;
      identifyPending = !running;
    }
    if (send) {
      sendIdentify(next);
    }
  }

  @Override
  public void stopped() {
    shutdown();
  }

  private void sendIdentify(ReplayItem.Identify next) {
    worker.run(() -> exporter.identify(next));
    queue.offer(next);
  }

  /** Who the session belongs to, as on Android: the resource, each context kind's key, and the key shown. */
  ReplayItem.Identify identify(Map<String, String> contextKeys, String canonicalKey, long timestamp,
      String session) {
    Map<String, String> attributes = new LinkedHashMap<>(resource);
    contextKeys.forEach((kind, key) -> {
      if (!key.isEmpty()) {
        attributes.put(kind, key);
      }
    });
    String canonical = canonicalKey != null ? canonicalKey : "unknown";
    String shown = friendlyName != null ? friendlyName : canonical;
    attributes.put("key", shown);
    attributes.put("canonicalKey", canonical);
    attributes.put("userIdentifier", shown);
    return new ReplayItem.Identify(attributes, timestamp, session);
  }

  // -- for tests --

  CaptureLoop loop() {
    return loop;
  }

  ReplayQueue queue() {
    return queue;
  }
}
