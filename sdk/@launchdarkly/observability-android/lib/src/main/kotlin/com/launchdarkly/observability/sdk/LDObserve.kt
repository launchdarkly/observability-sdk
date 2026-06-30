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

    override fun track(key: String, properties: Map<String, Any?>?, metricValue: Double?) {
        client.track(key, properties, metricValue)
    }

    override fun trackScreenView(name: String, screenClass: String?, screenId: String?, category: String?, properties: Map<String, Any?>?) {
        client.trackScreenView(name, screenClass, screenId, category, properties)
    }

    override fun trackClick(id: String?, tag: String?, text: String?, screenId: String?, x: Int?, y: Int?, properties: Map<String, Any?>?) {
        client.trackClick(id, tag, text, screenId, x, y, properties)
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
            override fun track(key: String, properties: Map<String, Any?>?, metricValue: Double?) {}
            override fun trackScreenView(name: String, screenClass: String?, screenId: String?, category: String?, properties: Map<String, Any?>?) {}
            override fun trackClick(id: String?, tag: String?, text: String?, screenId: String?, x: Int?, y: Int?, properties: Map<String, Any?>?) {}
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
         * @param observability      Configuration for observability telemetry.
         * @param replay Optional configuration for session replay. Pass `null` (the default)
         *                      to skip session replay initialization.
         * @param imageCaptureService Optional capture implementation for session replay.
         * @param customSessionId Optional session id to adopt instead of generating one, so this
         *                      instance can share a single `session.id` with another LaunchDarkly
         *                      SDK on the device. When null, a session id is generated automatically.
         */
        fun init(
            application: Application,
            mobileKey: String,
            ldContext: LDObserveContext,
            observability: ObservabilityOptions = ObservabilityOptions(),
            replay: ReplayOptions? = null,
            imageCaptureService: ImageCaptureServicing? = null,
            customSessionId: String? = null,
        ) {
            val logger = ObserveLogger.build(observability.logAdapter, observability.loggerName, observability.debug)

            val obsContext = ObservabilityContext(
                sdkKey = mobileKey,
                options = observability,
                application = application,
                logger = logger
            )

            val resource = buildObservabilityResource(sdkKey = mobileKey, options = observability)
            obsContext.resourceAttributes = resource.attributes

            // ObservabilityService and SessionReplayService install OpenTelemetry instrumentations
            // that touch UI / lifecycle state, so their construction must run on the main thread.
            // runOnMainThread blocks the caller until the work completes (via CountDownLatch), so
            // the SDK is ready as soon as init returns regardless of which thread called it.
            // NOTE: the calling thread must not hold any lock the main thread is waiting on, or
            // this will deadlock — see runOnMainThread KDoc.
            runOnMainThread {
                installObservability(application, mobileKey, resource, logger, observability, obsContext, customSessionId)
                if (replay != null) {
                    installSessionReplay(
                        replay,
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
            customSessionId: String? = null,
        ) {
            val service = ObservabilityService(
                application, mobileKey, resource, logger, options, customSessionId,
            )
            obsContext.sessionManager = service.sessionManager
            obsContext.userInteractionManager = service.userInteractionManager
            obsContext.screenViewFlow = service.screenViewFlow
            obsContext.screenViewManager = service.screenViewManager
            obsContext.trackFlow = service.trackFlow
            obsContext.appLifecycleFlow = service.appLifecycleFlow
            obsContext.appLaunchSignal = service.appLaunchSignal
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
        override fun track(key: String, properties: Map<String, Any?>?, metricValue: Double?) = delegate.track(key, properties, metricValue)
        override fun trackScreenView(name: String, screenClass: String?, screenId: String?, category: String?, properties: Map<String, Any?>?) = delegate.trackScreenView(name, screenClass, screenId, category, properties)
        override fun trackClick(id: String?, tag: String?, text: String?, screenId: String?, x: Int?, y: Int?, properties: Map<String, Any?>?) = delegate.trackClick(id, tag, text, screenId, x, y, properties)

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
