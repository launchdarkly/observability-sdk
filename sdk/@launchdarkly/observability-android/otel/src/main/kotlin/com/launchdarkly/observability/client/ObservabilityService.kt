package com.launchdarkly.observability.client

import com.launchdarkly.observability.InternalObservabilityApi
import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.bridge.emitLog
import com.launchdarkly.observability.bridge.toOtelAttributes
import com.launchdarkly.observability.client.screen.ScreenChange
import com.launchdarkly.observability.client.screen.ScreenStack
import com.launchdarkly.observability.client.screen.ScreenView
import com.launchdarkly.observability.client.screen.ScreenViewEvent
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import com.launchdarkly.observability.logs.EventLogRecordProcessor
import com.launchdarkly.observability.logs.OtlpLogExporter
import com.launchdarkly.observability.metrics.EventMetricExporter
import com.launchdarkly.observability.metrics.OtlpMetricExporter
import com.launchdarkly.observability.otlp.OtlpConfiguration
import com.launchdarkly.observability.otlp.OtlpHttpClient
import com.launchdarkly.observability.plugin.ObservabilityHookExporter
import com.launchdarkly.observability.plugin.TrackEmitting
import com.launchdarkly.observability.replay.transport.BatchWorker
import com.launchdarkly.observability.replay.transport.EventQueue
import com.launchdarkly.observability.sampling.ExportSampler
import com.launchdarkly.observability.sampling.NoopExportSampler
import com.launchdarkly.observability.sampling.SamplingLogProcessor
import com.launchdarkly.observability.traces.EventSpanProcessor
import com.launchdarkly.observability.traces.OtlpTraceExporter
import com.launchdarkly.observability.util.requireMainThread
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.metrics.DoubleGauge
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

/**
 * The [ObservabilityService] can be used for recording observability data such as
 * metrics, logs, errors, and traces.
 *
 * This class owns the OpenTelemetry pipeline: it configures the providers for logs, traces, and
 * metrics from the provided options, stamps every signal with the current session, and exports
 * over OTLP. It records only what it is asked to record.
 *
 * Automatic instrumentation is supplied separately through [instrumenting]. When that is null — the
 * OTel-only product — nothing is installed: no crash handler, no window callback wrapping, no
 * activity lifecycle screen detection, and no classpath scanning for OpenTelemetry Android
 * instrumentation. The full product passes an implementation that installs all of it.
 *
 * It is recommended to use the [com.launchdarkly.observability.plugin.Observability] plugin with the LaunchDarkly Android
 * Client SDK, as that will automatically initialize the [com.launchdarkly.observability.sdk.LDObserve] singleton instance.
 *
 * @param application The application instance.
 * @param sdkKey The SDK key for authentication.
 * @param resources The OpenTelemetry resource describing this service.
 * @param logger The logger for internal logging.
 * @param observabilityOptions Additional configuration options for the SDK.
 * @param customSessionId Optional session id to adopt (e.g. forwarded from another LaunchDarkly SDK
 *   on the device) so all signals share one `session.id`. Null lets the manager generate its own.
 * @param instrumenting Supplies automatic instrumentation. Null installs none.
 */
class ObservabilityService(
    override val application: Application,
    private val sdkKey: String,
    private val resources: Resource,
    override val logger: ObserveLogger,
    private val observabilityOptions: ObservabilityOptions,
    private val customSessionId: String? = null,
    private val instrumenting: ObservabilityInstrumenting? = null,
) : Observe, TrackEmitting, ObservabilityRuntime, OtelPipelineConfigurator {
    private val openTelemetry: OpenTelemetrySdk

    override val options: ObservabilityOptions get() = observabilityOptions
    override val session: LDSessionManaging get() = ldSessionManager
    override val currentScreen: Pair<String?, String?>
        get() = screenStack.currentScreenId to screenStack.currentScreenName

    // LaunchDarkly's own session manager. Owns session identity for all signals and can be seeded
    // with [customSessionId]. Exposed through [sessionManager] for Session Replay.
    private val ldSessionManager = LDSessionManager(
        initialSessionId = customSessionId,
        backgroundInactivityTimeout = observabilityOptions.sessionBackgroundTimeout,
        maxLifetime = 4.hours,
    )

    var sessionManager: LDSessionManaging? = ldSessionManager
        private set
    private var otelMeter: Meter
    private var otelLogger: Logger
    private var otelTracer: Tracer
    private val sampler: ExportSampler = instrumenting?.sampler ?: NoopExportSampler
    private val telemetryInspector: TelemetryInspector? = observabilityOptions.telemetryInspector
    private val otlpConfiguration = OtlpConfiguration(headers = observabilityOptions.customHeaders)
    private val eventQueue = EventQueue()
    private val batchWorker = BatchWorker(eventQueue = eventQueue, logger = logger)
    private var metricsReader: PeriodicMetricReader? = null
    private val gaugeCache = ConcurrentHashMap<String, DoubleGauge>()
    private val counterCache = ConcurrentHashMap<String, LongCounter>()
    private val histogramCache = ConcurrentHashMap<String, DoubleHistogram>()
    private val upDownCounterCache = ConcurrentHashMap<String, LongUpDownCounter>()
    @InternalObservabilityApi
    val hookExporter: ObservabilityHookExporter

    @Volatile
    private var cachedContextKeyAttributes: Attributes = Attributes.empty()

    /**
     * The single touch-capture hook, supplied by the instrumentation layer and shared with Session
     * Replay via [ObservabilityContext]. Null in the OTel-only product, which never observes
     * touches at all.
     */
    val userInteractionManager: UserInteractionManaging? = instrumenting?.makeUserInteractionManager()

    private val screenStack = ScreenStack()

    /**
     * Automatic screen-view capture, supplied by the instrumentation layer and shared with Session
     * Replay via [ObservabilityContext] so late-init paths can register an already-resumed
     * activity. Null in the OTel-only product, where screen views come only from `trackScreenView`.
     */
    var screenViewManager: ScreenViewCapturing? = null
        private set

    /**
     * Broadcasts each recorded screen view (first screen and every change) so Session Replay can
     * emit `Navigate` events. Shared with Session Replay via [ObservabilityContext.screenViewFlow].
     */
    private val _screenViewFlow = MutableSharedFlow<ScreenViewEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val screenViewFlow: SharedFlow<ScreenViewEvent> = _screenViewFlow.asSharedFlow()

    /**
     * Broadcasts each `track` event so Session Replay can emit a `Track` timeline event regardless
     * of the entry path (`LDClient.track` or [com.launchdarkly.observability.sdk.LDObserve.track],
     * including standalone init without `LDClient`). Shared via [ObservabilityContext.trackFlow].
     */
    private val _trackFlow = MutableSharedFlow<TrackEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val trackFlow: SharedFlow<TrackEvent> = _trackFlow.asSharedFlow()

    /**
     * Broadcasts each app-lifecycle transition (foreground/background) so Session Replay can emit
     * `Foreground` / `Background` breadcrumbs regardless of the span flags. Shared with Session
     * Replay via [ObservabilityContext.appLifecycleFlow].
     */
    private val _appLifecycleFlow = MutableSharedFlow<AppLifecycleSignal>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val appLifecycleFlow: SharedFlow<AppLifecycleSignal> = _appLifecycleFlow.asSharedFlow()

    /**
     * Tracks process foreground/background transitions and routes them through the single emitter
     * [handleAppLifecycleSignal]. Detection runs unconditionally so Session Replay breadcrumbs are
     * always available; the `app_foreground` / `app_background` span is gated separately by
     * [ObservabilityOptions.Analytics.appLifecycle].
     */
    private val appLifecycleTracker = AppLifecycleTracker(onSignal = { signal -> recordAppLifecycleSignal(signal) })

    /**
     * The one-shot app-launch signal, resolved synchronously during this service's `init` (before
     * Session Replay registers). Cached here rather than streamed so Session Replay can read it
     * directly when building its first wake-up batch — a stream would race the launch, which fires
     * before any subscriber exists. Wired into [ObservabilityContext.appLaunchSignal].
     */
    var appLaunchSignal: AppLaunchSignal? = null
        private set

    //TODO: Evaluate if this class should have a close/shutdown method to close this scope
    private val scope = CoroutineScope(DispatcherProviderHolder.current.io + SupervisorJob())

    init {
        requireMainThread { "ObservabilityService must be initialized on the main thread" }

        registerOtlpExporters()

        // Either an OpenTelemetry Android RUM-backed SDK (full product) or a plain one (OTel-only).
        // Both apply the same pipeline configuration, so the exported signals are identical apart
        // from the attributes and instrumentation that RUM contributes.
        openTelemetry = instrumenting?.createOpenTelemetry(application, ldSessionManager, this)
            ?: buildPlainOpenTelemetrySdk(ldSessionManager, this)

        // A new session (e.g. after a background timeout) must start with a fresh navigation
        // history, otherwise the first screen_view/Navigate of the new session would resolve
        // previous_screen against the prior session, and a re-appearing first screen would be
        // deduped instead of emitting a fresh navigation.
        //
        // Only reset on an actual session *change*. The initial session start carries
        // LDSession.NONE (empty id) as the previous session; resetting on it would clobber a first
        // screen that may already have been recorded by the time this notification fires.
        ldSessionManager.addObserver(object : LDSessionObserver {
            override fun onSessionStarted(newSession: LDSession, previousSession: LDSession) {
                if (previousSession.getId().isNotEmpty()) {
                    screenStack.reset()
                    // Re-seed the new session with the screen the user is still viewing. No
                    // onActivityResumed fires for an already-resumed activity, so without this the
                    // new session would have no opening screen_view span or Navigate event.
                    screenViewManager?.captureCurrentScreen()
                }
            }

            override fun onSessionEnded(session: LDSession) {}
        })

        otelMeter = openTelemetry.meterProvider.get(INSTRUMENTATION_SCOPE_NAME)
        otelLogger = openTelemetry.logsBridge.get(INSTRUMENTATION_SCOPE_NAME)
        otelTracer = openTelemetry.tracerProvider.get(INSTRUMENTATION_SCOPE_NAME)

        hookExporter = ObservabilityHookExporter(
            withSpans = true,
            withValue = true,
            tracerProvider = { getTracer() },
            contextFriendlyName = observabilityOptions.contextFriendlyName
        )
        // Route the afterTrack hook and identify context keys back into this service,
        // so it remains the single emitter of track spans.
        hookExporter.trackEmitter = this

        userInteractionManager?.let { interactions ->
            // Supply the active screen at touch-capture time. Read on the main thread when the touch
            // is captured (before it reaches app handlers), so a tap that navigates synchronously is
            // still stamped with the screen the user tapped rather than the destination screen.
            interactions.screenInfoProvider = {
                screenStack.currentScreenId to screenStack.currentScreenName
            }

            // Always track the current activity/window (benign: registers lifecycle callbacks only,
            // no window wrapping or hit-testing). This lets a later consumer - Session Replay
            // starting to record after the first activity is already running - enable capture and
            // wrap that already-current window instead of missing its touches.
            interactions.attachToApplication(application)
        }

        // Automatic screen detection routes appearing screens through the same single emitter used
        // by the manual trackScreenView API. The instrumentation layer decides whether detection is
        // enabled; the screen_view span itself is gated separately by analytics.screenViews.
        screenViewManager = instrumenting?.makeScreenViewCapture(application) { screen ->
            emitScreenView(screen)
        }
        screenViewManager?.start()

        // App-lifecycle detection runs unconditionally, because the session's background-inactivity
        // timeout depends on it (and Session Replay breadcrumbs need it). It observes the process
        // lifecycle only. The `app_foreground` / `app_background` spans are gated inside the handler.
        appLifecycleTracker.start()

        // Prime the session manager with the actual process state. [appLifecycleTracker] only
        // reports genuine foreground/background transitions (it suppresses the initial replay and
        // never emits a catch-up background), so if the SDK initializes while the app is already
        // backgrounded the manager would otherwise stay FOREGROUND and skip background-inactivity
        // rotation until the next foreground/background cycle. A genuine onStart will later settle
        // it back to foreground if the app comes forward.
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            ldSessionManager.onApplicationBackgrounded()
        }

        // Start the detectors. This resolves the app-launch signal synchronously, so it is cached
        // before Session Replay registers. Nothing happens here in the OTel-only product.
        instrumenting?.install(this)

        batchWorker.start()
    }

    private fun registerOtlpExporters() {
        val logExporter = OtlpLogExporter(
            httpClient = OtlpHttpClient(
                endpoint = observabilityOptions.otlpEndpoint + LOGS_PATH,
                config = otlpConfiguration,
            ),
        )
        val traceExporter = OtlpTraceExporter(
            httpClient = OtlpHttpClient(
                endpoint = observabilityOptions.otlpEndpoint + TRACES_PATH,
                config = otlpConfiguration,
            ),
        )
        val metricExporter = OtlpMetricExporter(
            httpClient = OtlpHttpClient(
                endpoint = observabilityOptions.otlpEndpoint + METRICS_PATH,
                config = otlpConfiguration,
            ),
        )
        batchWorker.addExporter(logExporter)
        batchWorker.addExporter(traceExporter)
        batchWorker.addExporter(metricExporter)
    }

    override fun configureLoggerProvider(sdkLoggerProviderBuilder: SdkLoggerProviderBuilder): SdkLoggerProviderBuilder {
        sdkLoggerProviderBuilder.setResource(resources)

        val delegates = buildList<LogRecordProcessor> {
            add(EventLogRecordProcessor(eventQueue = eventQueue, batchWorker = batchWorker))
            if (observabilityOptions.debug) {
                add(SimpleLogRecordProcessor.create(DebugLogExporter(logger)))
            }
            telemetryInspector?.let {
                add(SimpleLogRecordProcessor.create(it.logExporter))
            }
        }

        val otlpProcessor = SamplingLogProcessor(
            delegate = LogRecordProcessor.composite(delegates),
            sampler = sampler,
        )
        sdkLoggerProviderBuilder.addLogRecordProcessor(otlpProcessor)

        return sdkLoggerProviderBuilder
    }

    override fun configureTracerProvider(sdkTracerProviderBuilder: SdkTracerProviderBuilder): SdkTracerProviderBuilder {
        sdkTracerProviderBuilder.setResource(resources)

        val debugExporters = buildList<io.opentelemetry.sdk.trace.export.SpanExporter> {
            if (observabilityOptions.debug) {
                add(DebugSpanExporter(logger))
            }
            telemetryInspector?.let {
                add(it.spanExporter)
            }
        }
        val delegateExporter = if (debugExporters.isNotEmpty()) {
            io.opentelemetry.sdk.trace.export.SpanExporter.composite(debugExporters)
        } else {
            null
        }

        val otlpProcessor = EventSpanProcessor(
            eventQueue = eventQueue,
            sampler = sampler,
            delegateExporter = delegateExporter,
            batchWorker = batchWorker,
        )
        sdkTracerProviderBuilder.addSpanProcessor(otlpProcessor)

        return sdkTracerProviderBuilder
    }

    override fun configureMeterProvider(sdkMeterProviderBuilder: SdkMeterProviderBuilder): SdkMeterProviderBuilder {
        val eventExporter = EventMetricExporter(
            eventQueue = eventQueue,
            temporalitySelector = AggregationTemporalitySelector.deltaPreferred(),
            batchWorker = batchWorker,
        )

        val finalExporter: MetricExporter = if (observabilityOptions.debug || telemetryInspector != null) {
            CompositeMetricExporter(
                buildList {
                    add(eventExporter)
                    if (observabilityOptions.debug) add(DebugMetricExporter(logger))
                    telemetryInspector?.let { add(it.metricExporter) }
                }
            )
        } else {
            eventExporter
        }

        val metricReader = createPeriodicMetricReader(finalExporter)
        metricsReader = metricReader
        return sdkMeterProviderBuilder
            .setResource(resources)
            .registerMetricReader(metricReader)
    }

    private fun createPeriodicMetricReader(metricExporter: MetricExporter): PeriodicMetricReader {
        return PeriodicMetricReader.builder(metricExporter)
            .setInterval(METRICS_EXPORT_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    override fun recordMetric(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        val gauge = gaugeCache.getOrPut(metric.name) {
            otelMeter.gaugeBuilder(metric.name).build()
        }
        gauge.set(metric.value, metric.attributes.addSessionId())
    }

    override fun recordCount(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        // TODO: handle double casting to long better
        val counter = counterCache.getOrPut(metric.name) {
            otelMeter.counterBuilder(metric.name).build()
        }
        counter.add(metric.value.toLong(), metric.attributes.addSessionId())
    }

    override fun recordIncr(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        val counter = counterCache.getOrPut(metric.name) {
            otelMeter.counterBuilder(metric.name).build()
        }
        // It increments the value until the metric is exported, then it's reset.
        counter.add(1, metric.attributes.addSessionId())
    }

    override fun recordHistogram(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        val histogram = histogramCache.getOrPut(metric.name) {
            otelMeter.histogramBuilder(metric.name).build()
        }
        histogram.record(metric.value, metric.attributes.addSessionId())
    }

    override fun recordUpDownCounter(metric: Metric) {
        if (!observabilityOptions.metricsApi.enabled) return

        val upDownCounter = upDownCounterCache.getOrPut(metric.name) {
            otelMeter.upDownCounterBuilder(metric.name).build()
        }
        upDownCounter.add(metric.value.toLong(), metric.attributes.addSessionId())
    }

    override fun recordLog(
        message: String,
        severity: Severity,
        attributes: Attributes,
        spanContext: io.opentelemetry.api.trace.SpanContext?
    ) {
        if (observabilityOptions.logsApiLevel.level > severity.severityNumber) return
        otelLogger.emitLog(message, severity, attributes, spanContext)
    }

    override fun recordError(error: Error, attributes: Attributes) {
        if (!observabilityOptions.tracesApi.includeErrors) return

        val span = otelTracer
            .spanBuilder(ERROR_SPAN_NAME)
            .setSpanKind(SpanKind.CLIENT)
            .setParent(Context.current().with(Span.current()))
            .startSpan()

        val attrBuilder = Attributes.builder()
        attrBuilder.putAll(attributes)

        span.recordException(error, attrBuilder.build())
        span.end()
    }

    override fun startSpan(name: String, attributes: Attributes): Span {
        if (!observabilityOptions.tracesApi.includeSpans) return Span.getInvalid()

        return otelTracer.spanBuilder(name)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(attributes)
            .startSpan()
    }

    override fun track(key: String, properties: Map<String, Any?>?, metricValue: Double?) {
        track(key, metricValue, properties?.toOtelAttributes() ?: Attributes.empty(), contextKeyAttributes = null)
    }

    /**
     * Single emitter for `track` spans. Both the LD `afterTrack` hook and the
     * manual [com.launchdarkly.observability.sdk.LDObserve.track] path funnel through here.
     */
    override fun track(
        name: String,
        metricValue: Double?,
        attributes: Attributes,
        contextKeyAttributes: Attributes?
    ) {
        // Broadcast so Session Replay can record a `Track` timeline event for every track path,
        // independent of the span flags below (mirrors the `Navigate` broadcast in emitScreenView).
        // Carries only user-supplied track data, matching the previous SessionReplayHook payload.
        _trackFlow.tryEmit(TrackEvent(name = name, metricValue = metricValue, attributes = attributes))

        if (!observabilityOptions.analytics.trackEvents) return
        if (!observabilityOptions.tracesApi.includeSpans) return

        val attrBuilder = Attributes.builder()
        // Apply in increasing precedence so event identity can never be clobbered: user-supplied
        // track data first, then context keys, then the reserved key/value attributes last.
        attrBuilder.putAll(attributes)
        // Fresh context keys from the hook take precedence; otherwise use the cached identify keys.
        attrBuilder.putAll(contextKeyAttributes ?: cachedContextKeyAttributes)
        attrBuilder.put(AttributeKey.stringKey("key"), name)
        metricValue?.let { attrBuilder.put(AttributeKey.doubleKey("value"), it) }

        otelTracer.spanBuilder(TRACK_SPAN_NAME)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(attrBuilder.build())
            .startSpan()
            .end()
    }

    override fun trackScreenView(name: String, screenClass: String?, screenId: String?, category: String?, properties: Map<String, Any?>?) {
        emitScreenView(
            ScreenView(
                name = name,
                screenClass = screenClass,
                screenId = screenId,
                category = category,
                attributes = properties?.toOtelAttributes() ?: Attributes.empty()
            )
        )
    }

    /**
     * Manually emit a `click` span, mirroring the automatic tap instrumentation. Use this to
     * reproduce the taxonomy `click` event for interactions automatic capture can't observe.
     *
     * Gated by [ObservabilityOptions.Analytics.taps] (the same flag as automatic click spans) and
     * the global span flag. When [screenId] is `null`, the current tracked screen's id and name are
     * used so the click correlates with the active `screen_view`; when an explicit [screenId] is
     * supplied, `event.screen_name` is omitted (its name is unknown here) to avoid pairing one
     * screen's id with another's name. Reserved `event.*` fields take precedence over caller
     * [properties], matching the `screen_view`/`track` precedence model.
     */
    override fun trackClick(
        id: String?,
        tag: String?,
        text: String?,
        screenId: String?,
        x: Int?,
        y: Int?,
        properties: Map<String, Any?>?
    ) {
        if (!observabilityOptions.analytics.taps) return
        if (!observabilityOptions.tracesApi.includeSpans) return

        // Default to the current screen so the click correlates with the active `screen_view`. Only
        // pair the current screen's name when we actually defaulted to it; for a caller-supplied
        // `screenId` the matching name is unknown here, so omit `screen_name` rather than mismatch a
        // different screen's name with that id.
        val resolvedScreenId = screenId ?: screenStack.currentScreenId
        val resolvedScreenName = if (screenId == null) screenStack.currentScreenName else null

        val attrs = ClickAttributes.build(
            tag = tag,
            classname = null,
            id = id,
            text = text,
            screenId = resolvedScreenId,
            screenName = resolvedScreenName,
            x = x?.toLong(),
            y = y?.toLong(),
            contextKeyAttributes = cachedContextKeyAttributes,
            properties = properties?.toOtelAttributes() ?: Attributes.empty(),
        )

        recordClickSpan(attrs, startTimeMs = null, endTimeMs = null)
    }

    /**
     * Emits a `click` span. Shared by [trackClick] and the automatic tap instrumentation, which
     * supplies the timestamps of the touch that produced it.
     */
    override fun recordClickSpan(attributes: Attributes, startTimeMs: Long?, endTimeMs: Long?) {
        val builder = otelTracer.spanBuilder(CLICK_SPAN_NAME)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(attributes)
        startTimeMs?.let { builder.setStartTimestamp(it, TimeUnit.MILLISECONDS) }
        val span = builder.startSpan()
        if (endTimeMs != null) span.end(endTimeMs, TimeUnit.MILLISECONDS) else span.end()
    }

    /**
     * Single funnel for screen changes. Both the automatic [ScreenViewCapturing] capture and the
     * manual [trackScreenView] API route through here so `previous_screen` resolution stays
     * consistent.
     *
     * The navigation broadcast (Session Replay `Navigate`) always fires once a screen is recorded;
     * the `screen_view` span is gated by [ObservabilityOptions.Analytics.screenViews].
     *
     * Re-appearances of the already-current screen (e.g. an activity resumed again after returning
     * from an overlay, permission dialog, or the recents switcher) are dropped so they don't emit
     * duplicate `screen_view` spans or `Navigate` events.
     */
    override fun recordScreenView(screen: ScreenView) = emitScreenView(screen)

    fun emitScreenView(screen: ScreenView) {
        // Resolve previous_screen against the shared stack before recording this one. A
        // re-appearance of the current screen is not a navigation, so emit nothing. Identity is
        // keyed on screenId (when present) so distinct screens sharing a display name aren't
        // collapsed into a re-appearance of one another.
        val change = screenStack.record(screen.name, screen.screenId)
        if (change !is ScreenChange.Changed) return
        val previous = change.previous

        // Broadcast the navigation so Session Replay can emit a `Navigate` event, independent of
        // the screen_view span flag.
        _screenViewFlow.tryEmit(ScreenViewEvent(name = screen.name, previousName = previous, timestamp = screen.timestamp))

        if (!observabilityOptions.analytics.screenViews) return
        if (!observabilityOptions.tracesApi.includeSpans) return

        val attrBuilder = Attributes.builder()
        // Apply in increasing precedence so the screen-view taxonomy can never be clobbered: caller
        // properties first, then identify context keys, then the reserved `event.*` fields last
        // (matching the track path).
        attrBuilder.putAll(screen.attributes)
        attrBuilder.putAll(cachedContextKeyAttributes)
        attrBuilder.put(EVENT_NAME, screen.name)
        screen.screenClass?.let { attrBuilder.put(EVENT_SCREEN_CLASS, it) }
        screen.screenId?.let { attrBuilder.put(EVENT_SCREEN_ID, it) }
        previous?.let { attrBuilder.put(EVENT_PREVIOUS_SCREEN, it) }
        screen.category?.let { attrBuilder.put(EVENT_CATEGORY, it) }

        otelTracer.spanBuilder(SCREEN_VIEW_SPAN_NAME)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(attrBuilder.build())
            .startSpan()
            .end()
    }

    /**
     * Single funnel for app-lifecycle transitions from [appLifecycleTracker].
     *
     * The breadcrumb broadcast (Session Replay `Foreground` / `Background`) always fires; the
     * `app_foreground` / `app_background` span is gated by
     * [ObservabilityOptions.Analytics.appLifecycle] (and the global span flag), mirroring the
     * navigation/track emitters.
     *
     * The span is additionally suppressed without an instrumentation provider: an app-lifecycle
     * span is derived from observing the process rather than from an explicit call, so it belongs
     * to the full product even though the detection itself runs here to drive session rotation.
     */
    override fun recordAppLifecycleSignal(signal: AppLifecycleSignal) {
        // Drive the session manager's background-inactivity timeout from the same lifecycle source.
        when (signal.kind) {
            AppLifecycleSignal.Kind.FOREGROUND -> ldSessionManager.onApplicationForegrounded()
            AppLifecycleSignal.Kind.BACKGROUND -> ldSessionManager.onApplicationBackgrounded()
        }

        // Broadcast so Session Replay can emit a breadcrumb independent of the span flags below.
        _appLifecycleFlow.tryEmit(signal)

        if (instrumenting == null) return
        if (!observabilityOptions.analytics.appLifecycle) return
        if (!observabilityOptions.tracesApi.includeSpans) return

        val spanName = when (signal.kind) {
            AppLifecycleSignal.Kind.FOREGROUND -> APP_FOREGROUND_SPAN_NAME
            AppLifecycleSignal.Kind.BACKGROUND -> APP_BACKGROUND_SPAN_NAME
        }

        val attrBuilder = Attributes.builder()
        // Context keys first so the reserved `event.*` taxonomy fields always win.
        attrBuilder.putAll(cachedContextKeyAttributes)
        signal.lifecycleState?.let { attrBuilder.put(EVENT_LIFECYCLE_STATE, it) }

        otelTracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(attrBuilder.build())
            .startSpan()
            .end()
    }

    /**
     * Single funnel for the app-launch signal. Broadcasts it so Session Replay can record a `Launch`
     * breadcrumb (always), then emits the taxonomy `app_launch` span only when gated on by
     * [ObservabilityOptions.Analytics.appLaunch] (and the global span flag).
     *
     * The cold/warm startup-performance dimension is attached as an `app.start` span event (taxonomy
     * §4.6) only when [ObservabilityOptions.Instrumentations.launchTime] is enabled. That flag also
     * gates the legacy TTID/TTFD histogram metrics; when it is off the span is anchored at the
     * launch-detection time so it carries no startup duration.
     */
    override fun recordAppLaunchSignal(signal: AppLaunchSignal) {
        // Cache before the span flag checks so Session Replay can emit the `Launch` breadcrumb
        // independent of the span flags below. Runs synchronously during init, so the signal is
        // available before Session Replay reads it.
        appLaunchSignal = signal

        if (!observabilityOptions.analytics.appLaunch) return
        if (!observabilityOptions.tracesApi.includeSpans) return

        val attrBuilder = Attributes.builder()
        // Context keys first so the reserved `event.*` taxonomy fields always win.
        attrBuilder.putAll(cachedContextKeyAttributes)
        attrBuilder.put(EVENT_LAUNCH_TYPE, signal.launchType.wireValue)
        signal.version?.let { attrBuilder.put(EVENT_VERSION, it) }
        signal.build?.let { attrBuilder.put(EVENT_BUILD, it) }
        signal.previousVersion?.let { attrBuilder.put(EVENT_PREVIOUS_VERSION, it) }

        // The span represents the launch itself, not the (later) point where this handler runs after
        // startup work. Anchor it to process start (the launch-detection time minus the measured
        // startup duration) and end it at the launch-detection time carried by the signal, so
        // analytics timestamps reflect the real startup window and aren't skewed by SDK init work.
        val launchTimeMs = signal.timestamp

        // The startup-performance dimension (cold/warm `start.type` + `start.duration_ms`) is gated by
        // instrumentations.launchTime. When it is off we also anchor the span at the launch-detection
        // time instead of back-dating it to process start, so the span window carries no startup
        // duration and `start.duration_ms` can't be recovered from it.
        val includeLaunchTime = observabilityOptions.instrumentations.launchTime
        val spanStartMs = if (includeLaunchTime) {
            signal.startDurationMs?.let { launchTimeMs - it } ?: launchTimeMs
        } else {
            launchTimeMs
        }

        val span = otelTracer.spanBuilder(APP_LAUNCH_SPAN_NAME)
            .setSpanKind(SpanKind.CLIENT)
            .setAllAttributes(attrBuilder.build())
            .setStartTimestamp(spanStartMs, TimeUnit.MILLISECONDS)
            .startSpan()
        if (includeLaunchTime) {
            signal.startType?.let { startType ->
                val eventAttrs = Attributes.builder().put(START_TYPE, startType.wireValue)
                signal.startDurationMs?.let { eventAttrs.put(START_DURATION_MS, it) }
                // Place the event at the launch-detection time so it falls within the span window.
                span.addEvent(APP_START_EVENT_NAME, eventAttrs.build(), launchTimeMs, TimeUnit.MILLISECONDS)
            }
        }
        span.end(launchTimeMs, TimeUnit.MILLISECONDS)
    }

    override fun updateCachedContextKeys(contextKeys: Map<String, String>) {
        val builder = Attributes.builder()
        for ((k, v) in contextKeys) {
            builder.put(AttributeKey.stringKey(k), v)
        }
        cachedContextKeyAttributes = builder.build()
    }

    /**
     * Returns the tracer instance for creating spans.
     *
     * @return Tracer instance
     */
    override fun getTracer(): Tracer = otelTracer

    /**
     * Returns the logger instance for recording log records.
     *
     * @return Logger instance
     */
    fun getLogger(): io.opentelemetry.api.logs.Logger = otelLogger

    /**
     * Requests a flush of all pending telemetry data (traces, logs, metrics).
     *
     * Export to the network happens asynchronously via [BatchWorker] on background dispatchers;
     * this call does not wait for the queue to drain.
     *
     * Logs and spans are enqueued synchronously from their processors' `onEmit` / `onEnd`, so
     * nothing is buffered inside the processors — no processor flush is required.
     *
     * Metrics are different: [PeriodicMetricReader] collects instrument values on an interval
     * and only pushes them to the exporter (our [EventMetricExporter]) when the interval
     * elapses or when `forceFlush()` is invoked. That call is itself asynchronous — it schedules
     * collection on the reader's executor and completes only once the samples have been handed
     * to the exporter — so we join on its [CompletableResultCode] (with a small timeout) before
     * signalling the batch worker. That guarantees metric samples are enqueued before the
     * worker starts draining.
     */
    override fun flush() {
        metricsReader?.forceFlush()?.join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        batchWorker.flush()
    }

    override fun recordInstrumentationHistogram(metric: Metric) {
        val histogram = histogramCache.getOrPut(metric.name) {
            otelMeter.histogramBuilder(metric.name).build()
        }
        histogram.record(metric.value, metric.attributes.addSessionId())
    }

    private fun Attributes.addSessionId() = this.toBuilder().put(SESSION_ID_ATTRIBUTE, ldSessionManager.getSessionId()).build()

    companion object {
        private const val METRICS_PATH = "/v1/metrics"
        private const val LOGS_PATH = "/v1/logs"
        private const val TRACES_PATH = "/v1/traces"
        private const val INSTRUMENTATION_SCOPE_NAME = "com.launchdarkly.observability"
        const val ERROR_SPAN_NAME = "highlight.error"
        const val TRACK_SPAN_NAME = "track"
        const val SCREEN_VIEW_SPAN_NAME = "screen_view"
        const val APP_FOREGROUND_SPAN_NAME = "app_foreground"
        const val APP_BACKGROUND_SPAN_NAME = "app_background"
        const val APP_LAUNCH_SPAN_NAME = "app_launch"
        const val APP_START_EVENT_NAME = "app.start"
        const val CLICK_SPAN_NAME = ClickAttributes.CLICK_SPAN_NAME
        const val SESSION_ID_ATTRIBUTE = "session.id"

        // `event.*` taxonomy attribute keys (see analytics-taxonomy.md). Click-specific keys live
        // in [ClickAttributes], which is kept free of Android-framework dependencies.
        private val EVENT_NAME = AttributeKey.stringKey("event.name")
        private val EVENT_SCREEN_CLASS = AttributeKey.stringKey("event.screen_class")
        private val EVENT_SCREEN_ID = AttributeKey.stringKey("event.screen_id")
        private val EVENT_PREVIOUS_SCREEN = AttributeKey.stringKey("event.previous_screen")
        private val EVENT_CATEGORY = AttributeKey.stringKey("event.category")
        private val EVENT_LIFECYCLE_STATE = AttributeKey.stringKey("event.lifecycle_state")
        private val EVENT_LAUNCH_TYPE = AttributeKey.stringKey("event.launch_type")
        private val EVENT_VERSION = AttributeKey.stringKey("event.version")
        private val EVENT_BUILD = AttributeKey.stringKey("event.build")
        private val EVENT_PREVIOUS_VERSION = AttributeKey.stringKey("event.previous_version")
        private val START_TYPE = AttributeKey.stringKey("start.type")
        private val START_DURATION_MS = AttributeKey.longKey("start.duration_ms")

        private const val METRICS_EXPORT_INTERVAL_MS = 10_000L

        // Upper bound on how long [flush] waits for the metric reader to finish collecting
        // pending instrument values and hand them to the exporter. Kept short so flush stays
        // responsive even if the reader's executor is backed up.
        private const val FLUSH_TIMEOUT_SECONDS = 5L
    }
}
