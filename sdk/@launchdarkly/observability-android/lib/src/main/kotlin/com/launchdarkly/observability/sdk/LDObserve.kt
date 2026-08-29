package com.launchdarkly.observability.sdk

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.bridge.AttributeConverter
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.client.ObservabilityService
import com.launchdarkly.observability.client.UserInteractionManager
import com.launchdarkly.observability.client.buildObservabilityResource
import com.launchdarkly.observability.client.readInjectedSymbolsId
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.capture.ImageCaptureServicing
import com.launchdarkly.observability.replay.plugin.SessionReplayPluginImpl
import com.launchdarkly.observability.util.runOnMainThread
import com.launchdarkly.sdk.android.LDClient
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

    override fun identify(contextKeys: Map<String, String>, canonicalKey: String, attributes: Map<String, Any?>?) {
        client.identify(contextKeys, canonicalKey, attributes)
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
            override fun identify(contextKeys: Map<String, String>, canonicalKey: String, attributes: Map<String, Any?>?) {}
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

        /**
         * Whether observability is attached to an initialized [LDClient], which is the case when the
         * plugin was registered with one — through [init] or
         * [com.launchdarkly.sdk.android.LDConfig.Builder.plugins].
         *
         * `false` after a standalone [init], where no feature-flag SDK is present: nothing can be
         * evaluated, [LDClient.identify] and `LDClient.track` are unavailable, and telemetry is
         * attributed through [identify] instead. Hosts that support both setups can read this to hide
         * what the flagging SDK would drive.
         */
        @Volatile
        var isFlagClientInitialized: Boolean = false
            internal set

        @Volatile
        internal var observabilityClient: ObservabilityService? = null
            private set

        fun init(client: ObservabilityService) {
            observabilityClient = client
            delegate = LDObserve(client)
        }

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
            // First thing this path does, on the caller's thread: the touch hook only learns about an
            // activity through lifecycle callbacks, and those are not replayed, so every millisecond
            // spent before attaching (building the resource below reads app assets, for instance) is a
            // millisecond in which the activity can resume unseen. Losing that race is recoverable -
            // ObservabilityService adopts the visible activity - but winning it keeps the common case
            // callback-driven.
            val userInteractionManager = UserInteractionManager().apply {
                attachToApplication(application)
            }

            val logger = ObserveLogger.build(observability.logAdapter, observability.loggerName, observability.debug)

            val obsContext = ObservabilityContext(
                sdkKey = mobileKey,
                options = observability,
                application = application,
                logger = logger
            )

            val resource = buildObservabilityResource(
                sdkKey = mobileKey,
                options = observability,
                symbolsId = readInjectedSymbolsId(application),
            )
            obsContext.resourceAttributes = resource.attributes

            // ObservabilityService and SessionReplayService install OpenTelemetry instrumentations
            // that touch UI / lifecycle state, so their construction must run on the main thread.
            // runOnMainThread blocks the caller until the work completes (via CountDownLatch), so
            // the SDK is ready as soon as init returns regardless of which thread called it.
            // NOTE: the calling thread must not hold any lock the main thread is waiting on, or
            // this will deadlock — see runOnMainThread KDoc.
            runOnMainThread {
                installObservability(
                    application, mobileKey, resource, logger, observability, obsContext,
                    customSessionId, userInteractionManager,
                )
                if (replay != null) {
                    installSessionReplay(
                        replay,
                        obsContext,
                        imageCaptureService,
                    )
                }
            }

            seedInitialIdentify(ldContext)
        }

        /**
         * Initialization that attaches observability (and optionally session replay) to an already
         * initialized [LDClient], so that flag evaluations, identify calls, and track calls made
         * through that client are instrumented.
         *
         * This takes the same arguments as the standalone [init] above, differing only in that the
         * environment comes from [ldClient] rather than a mobile key. Prefer it over passing
         * [Observability] and [SessionReplay] to
         * [com.launchdarkly.sdk.android.LDConfig.Builder.plugins]: the client's configuration is
         * left alone, and observability is set up the same way whether or not the flagging SDK is
         * involved.
         *
         * @param application The Android [Application] instance.
         * @param ldClient    The initialized [LDClient] to instrument.
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
            ldClient: LDClient,
            ldContext: LDObserveContext,
            observability: ObservabilityOptions = ObservabilityOptions(),
            replay: ReplayOptions? = null,
            imageCaptureService: ImageCaptureServicing? = null,
            customSessionId: String? = null,
        ) {
            // Constructed before the main-thread hop so its touch hook starts watching activity
            // lifecycle callbacks now, for the reason the plugin's own field documents.
            val observabilityPlugin = Observability(
                application = application,
                options = observability,
                customSessionId = customSessionId,
                expectedMobileKey = null,
            )

            // Observability and session replay both install OpenTelemetry instrumentations as they
            // come up, for the same reasons the standalone init above documents, so this runs on the
            // main thread and blocks the caller until the SDK is ready.
            runOnMainThread {
                // Observability goes first: installing session replay needs the ObservabilityContext
                // that registering observability publishes.
                ldClient.registerPlugin(observabilityPlugin)
                if (replay != null) {
                    installSessionReplayForClient(replay, observability, imageCaptureService)
                }
            }

            seedInitialIdentify(ldContext)
        }

        /**
         * Installs session replay onto the [ObservabilityContext] that registering [Observability]
         * with the client published.
         *
         * Replay contributes no hooks and reads nothing off the [LDClient], so registering it as a
         * plugin would do no more than forward to the same install the standalone path performs.
         * Installing it directly keeps both paths on one code path and hands over the context
         * explicitly instead of leaving the plugin to look it up globally.
         *
         * Must run on the main thread; called from inside the [runOnMainThread] block in [init].
         */
        private fun installSessionReplayForClient(
            replayOptions: ReplayOptions,
            observability: ObservabilityOptions,
            imageCaptureService: ImageCaptureServicing?,
        ) {
            val obsContext = context ?: run {
                ObserveLogger.build(observability.logAdapter, observability.loggerName, observability.debug)
                    .error("Observability is not installed; skipping session replay")
                return
            }
            installSessionReplay(replayOptions, obsContext, imageCaptureService)
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
            userInteractionManager: UserInteractionManager = UserInteractionManager(),
        ) {
            val service = ObservabilityService(
                application, mobileKey, resource, logger, options, customSessionId,
                userInteractionManager,
            )
            obsContext.sessionManager = service.sessionManager
            obsContext.userInteractionManager = service.userInteractionManager
            obsContext.screenViewFlow = service.screenViewFlow
            obsContext.screenViewManager = service.screenViewManager
            obsContext.trackFlow = service.trackFlow
            obsContext.identifyFlow = service.identifyFlow
            obsContext.appLifecycleFlow = service.appLifecycleFlow
            obsContext.appLaunchSignal = service.appLaunchSignal
            context = obsContext
            init(service)
        }

        /**
         * Creates the Session Replay plugin and registers + initializes it, which drains any
         * pre-init buffer in [LDReplay].
         *
         * Must run on the main thread; called from inside the [runOnMainThread] block in [init].
         */
        private fun installSessionReplay(
            replayOptions: ReplayOptions,
            obsContext: ObservabilityContext,
            imageCaptureService: ImageCaptureServicing? = null,
        ) {
            val plugin = SessionReplayPluginImpl(replayOptions, imageCaptureService)
            plugin.register(obsContext)
            plugin.initialize()
        }

        /**
         * Records the context this SDK starts with, so telemetry is attributed to it instead of
         * waiting for the app's next identify.
         *
         * Both paths need it: `LDClient` performs its initial identify before a plugin registered on
         * a running client can hook it, and the standalone path has no client to identify at all. It
         * goes through the same funnel as every other identify, so the context keys are cached for
         * later spans, the `LD.identify` log is emitted and session replay is identified — driving
         * replay directly instead would leave observability itself unattributed.
         *
         * Called after session replay is installed, because the identify is broadcast rather than
         * buffered: a replay service that does not exist yet would never see it. Runs off the
         * calling thread since the identify is exported over the network.
         */
        private fun seedInitialIdentify(ldContext: LDObserveContext) {
            val contextKeys = contextKeysOf(ldContext)
            if (contextKeys.isEmpty()) return
            CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                identify(contextKeys, ldContext.fullyQualifiedKey, attributes = null)
            }
        }

        /**
         * Kind -> key pairs for [ldContext], the shape the identify funnel takes. Keyless
         * sub-contexts are skipped, matching what session replay puts on an identify payload.
         */
        private fun contextKeysOf(ldContext: LDObserveContext): Map<String, String> {
            if (!ldContext.isMultiple) {
                return if (ldContext.key.isEmpty()) emptyMap() else mapOf(ldContext.kind to ldContext.key)
            }
            return (0 until ldContext.individualContextCount)
                .map { ldContext.getIndividualContext(it) }
                .filter { it.key.isNotEmpty() }
                .associate { it.kind to it.key }
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
        override fun identify(contextKeys: Map<String, String>, canonicalKey: String, attributes: Map<String, Any?>?) = delegate.identify(contextKeys, canonicalKey, attributes)
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
