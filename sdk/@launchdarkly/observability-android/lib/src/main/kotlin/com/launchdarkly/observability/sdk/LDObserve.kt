package com.launchdarkly.observability.sdk

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.bridge.AttributeConverter
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.client.ObservabilityService
import com.launchdarkly.observability.client.buildObservabilityResource
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.capture.ImageCaptureServicing
import com.launchdarkly.observability.replay.plugin.SessionReplayPluginImpl
import com.launchdarkly.observability.util.runOnMainThread
import com.launchdarkly.sdk.LDValue
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.sdk.resources.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * LDObserve is the singleton entry point for recording observability data such as
 * metrics, logs, errors, and traces. It is recommended to use the [com.launchdarkly.observability.plugin.Observability] plugin
 * with the LaunchDarkly Android Client SDK, as that will automatically initialize the [LDObserve] singleton instance.
 *
 * @constructor Creates an LDObserve instance with the provided [Observe].
 * @param client The [Observe] to which observability data will be forwarded.
 */
class LDObserve(private val client: Observe) : Observe {

    override fun recordMetric(metric: Metric) {
        client.recordMetric(metric)
    }

    override fun recordCount(metric: Metric) {
        client.recordCount(metric)
    }

    override fun recordIncr(metric: Metric) {
        client.recordIncr(metric)
    }

    override fun recordHistogram(metric: Metric) {
        client.recordHistogram(metric)
    }

    override fun recordUpDownCounter(metric: Metric) {
        client.recordUpDownCounter(metric)
    }

    override fun recordError(error: Error, attributes: Attributes) {
        client.recordError(error, attributes)
    }

    override fun recordLog(message: String, severity: Severity, attributes: Attributes, spanContext: SpanContext?) {
        client.recordLog(message, severity, attributes, spanContext)
    }

    override fun startSpan(name: String, attributes: Attributes): Span {
        return client.startSpan(name, attributes)
    }

    override fun flush() {
        client.flush()
    }

    companion object : Observe {
        // initially a no-op delegate
        // volatile annotation guarantees multiple threads see the same value after init and none continue using the no-op implementation
        @Volatile
        private var delegate: Observe = object : Observe {
            override fun recordMetric(metric: Metric) {}
            override fun recordCount(metric: Metric) {}
            override fun recordIncr(metric: Metric) {}
            override fun recordHistogram(metric: Metric) {}
            override fun recordUpDownCounter(metric: Metric) {}
            override fun recordError(error: Error, attributes: Attributes) {}
            override fun recordLog(message: String, severity: Severity, attributes: Attributes, spanContext: SpanContext?) {}
            override fun startSpan(name: String, attributes: Attributes): Span {
                return Span.getInvalid()
            }
            override fun flush() {}
        }

        /**
         * Shared context for other plugins (e.g. Session Replay) to access Observability configuration and dependencies.
         */
        @Volatile
        var context: ObservabilityContext? = null
            internal set

        @Volatile
        internal var observabilityClient: ObservabilityService? = null
            private set

        fun init(client: ObservabilityService) {
            observabilityClient = client
            delegate = LDObserve(client)
        }

        @Volatile
        private var sessionReplayPlugin: SessionReplayPluginImpl? = null

        /**
         * Standalone initialization that sets up observability (and optionally session replay)
         * without requiring [com.launchdarkly.sdk.android.LDClient].
         *
         * Use this when you want observability and/or session replay to run independently of the
         * LaunchDarkly feature-flag SDK.
         *
         * @param application The Android [Application] instance.
         * @param mobileKey   The LaunchDarkly mobile key used for authentication.
         * @param ldContext    The [LDObserveContext] identifying the current user/context.
         * @param options      Configuration for observability telemetry.
         * @param replayOptions Optional configuration for session replay. Pass `null` (the default)
         *                      to skip session replay initialization.
         * @param imageCaptureService Optional capture implementation for session replay.
         */
        fun init(
            application: Application,
            mobileKey: String,
            ldContext: LDObserveContext,
            options: ObservabilityOptions = ObservabilityOptions(),
            replayOptions: ReplayOptions? = null,
            imageCaptureService: ImageCaptureServicing? = null,
        ) {
            val logger = ObserveLogger.build(options.logAdapter, options.loggerName, options.debug)

            val obsContext = ObservabilityContext(
                sdkKey = mobileKey,
                options = options,
                application = application,
                logger = logger
            )

            val resource = buildObservabilityResource(sdkKey = mobileKey, options = options)
            obsContext.resourceAttributes = resource.attributes

            // ObservabilityService and SessionReplayService install OpenTelemetry instrumentations
            // that touch UI / lifecycle state, so their construction must run on the main thread.
            // runOnMainThread blocks the caller until the work completes (via CountDownLatch), so
            // the SDK is ready as soon as init returns regardless of which thread called it.
            // NOTE: the calling thread must not hold any lock the main thread is waiting on, or
            // this will deadlock — see runOnMainThread KDoc.
            runOnMainThread {
                installObservability(application, mobileKey, resource, logger, options, obsContext)
                if (replayOptions != null) {
                    installSessionReplay(
                        replayOptions,
                        obsContext,
                        ldContext,
                        imageCaptureService,
                    )
                }
            }
        }

        /**
         * Constructs the [ObservabilityService], publishes it as the active [LDObserve] delegate,
         * and finishes wiring [obsContext] (sessionManager + global publication).
         *
         * Must run on the main thread; called from inside the [runOnMainThread] block in [init].
         */
        private fun installObservability(
            application: Application,
            mobileKey: String,
            resource: Resource,
            logger: ObserveLogger,
            options: ObservabilityOptions,
            obsContext: ObservabilityContext,
        ) {
            val service = ObservabilityService(
                application, mobileKey, resource, logger, options,
            )
            obsContext.sessionManager = service.sessionManager
            context = obsContext
            init(service)
        }

        /**
         * Creates the Session Replay plugin, registers + initializes it (which drains any pre-init
         * buffer in [LDReplay]), and — only if the underlying service was actually installed and
         * published — kicks off the initial identify in the background.
         *
         * Must run on the main thread; called from inside the [runOnMainThread] block in [init].
         */
        private fun installSessionReplay(
            replayOptions: ReplayOptions,
            obsContext: ObservabilityContext,
            ldContext: LDObserveContext,
            imageCaptureService: ImageCaptureServicing? = null,
        ) {
            val plugin = SessionReplayPluginImpl(replayOptions, imageCaptureService)
            sessionReplayPlugin = plugin
            plugin.register(obsContext)
            if (!plugin.initialize()) return
            val replayService = plugin.sessionReplayService ?: return
            CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                replayService.identifySession(ldContext)
            }
        }

        override fun recordMetric(metric: Metric) = delegate.recordMetric(metric)
        override fun recordCount(metric: Metric) = delegate.recordCount(metric)
        override fun recordIncr(metric: Metric) = delegate.recordIncr(metric)
        override fun recordHistogram(metric: Metric) = delegate.recordHistogram(metric)
        override fun recordUpDownCounter(metric: Metric) = delegate.recordUpDownCounter(metric)
        override fun recordError(error: Error, attributes: Attributes) = delegate.recordError(error, attributes)
        override fun recordLog(message: String, severity: Severity, attributes: Attributes, spanContext: SpanContext?) = delegate.recordLog(message, severity, attributes, spanContext)
        override fun startSpan(name: String, attributes: Attributes): Span = delegate.startSpan(name, attributes)
        override fun flush() = delegate.flush()

        /**
         * Emits a `launchdarkly.track` span to the o11y backend.
         *
         * The same span is emitted automatically when application code calls `LDClient.track(...)`
         * (via the SDK's `afterTrack` hook). This direct entry point lets callers ship a track
         * event without going through the LD client — useful for ad-hoc telemetry, sample apps,
         * or tests.
         *
         * Behavior:
         *  - When [ObservabilityOptions.productAnalyticsApi]'s `trackEvents` is `false`, this
         *    call is a no-op (matches the `afterTrack` hook).
         *  - When called before `LDObserve.init(...)` runs, this is a silent no-op (the
         *    underlying [ObservabilityService] is not yet wired).
         *  - All internal exceptions are caught and logged at debug, never surfaced to the
         *    caller — track-event emission is best-effort and must never break the host app.
         *
         * @param key         the track event key, written as the `key` attribute on the span
         * @param data        optional structured payload; properties are spread as span
         *                    attributes when this is an [com.launchdarkly.sdk.LDValueType.OBJECT].
         *                    Non-object / null payloads are skipped (no spread, no throw).
         * @param metricValue optional numeric value, written as the `value` attribute on the span.
         */
        @JvmStatic
        @JvmOverloads
        fun track(key: String, data: LDValue? = null, metricValue: Double? = null) {
            // Belt-and-suspenders: the downstream [ObservabilityHookExporter.track] already
            // wraps its body in try/catch, but we mirror the RN hook pattern here so a failure
            // in *any* layer above the exporter (pre-init guard expansion, future service
            // changes, etc.) still honors this method's KDoc contract: "All internal exceptions
            // are caught and logged at debug, never surfaced to the caller."
            try {
                // Pre-init guard: if no service is installed yet (developer called track before
                // Observability.onPluginsReady / standalone LDObserve.init), there's no tracer to
                // emit on. Silently no-op rather than crashing or buffering.
                val service = observabilityClient ?: return
                service.track(key, data, metricValue)
            } catch (_: Throwable) {
                // Swallow: track-event emission is best-effort and must never break the host app.
                // The exporter already logs the underlying cause when reachable; this outer
                // catch is for the (rare) case where a failure precedes the exporter call.
            }
        }

        /**
         * Bridge-friendly overloads that avoid exposing OpenTelemetry types
         * to callers such as the .NET MAUI native bridge.
         */

        fun recordError(message: String, cause: String? = null) {
            val error = Error(message, if (cause != null) Throwable(cause) else null)
            delegate.recordError(error, Attributes.empty())
        }

        fun recordLog(message: String, severityNumber: Int, attributes: Map<String, Any?>? = null) {
            val severity = Severity.entries.firstOrNull { it.severityNumber == severityNumber }
                ?: Severity.INFO
            val attrs = AttributeConverter.convert(attributes)
            delegate.recordLog(message, severity, attrs)
        }
    }
}
