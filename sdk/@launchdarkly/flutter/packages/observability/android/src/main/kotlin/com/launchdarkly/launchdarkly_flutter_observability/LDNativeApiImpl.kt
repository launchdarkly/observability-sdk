package com.launchdarkly.launchdarkly_flutter_observability

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.bridge.LDObserveBridge
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.replay.PrivacyProfile
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import io.flutter.plugin.common.MethodChannel
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Pigeon [LDNativeApi] implementation. Ported (line-for-line where it makes
 * sense) from `ObservabilityBridge.kt` in
 * `sdk/@launchdarkly/mobile-dotnet/android/native/LDObserve/.../ObservabilityBridge.kt`,
 * with the Pigeon-generated wire DTOs taking the place of the .NET-side
 * `LDObservabilityOptions` / `LDSessionReplayOptions` Java types.
 *
 * The current host [Activity] is set by the plugin via [ActivityAware] and
 * forwarded to `LDReplay.registerActivity(...)` after `LDObserve.init` so the
 * SDK has the activity it missed during `onActivityCreated`. This mirrors the
 * RN adapter at
 * `sdk/@launchdarkly/react-native-ld-session-replay/.../SessionReplayClientAdapter.kt`,
 * which has the same comment.
 */
internal class LDNativeApiImpl(
    private val application: Application,
    private val captureChannel: MethodChannel,
) : LDNativeApi {

    @Volatile
    var activity: Activity? = null

    override fun start(
        mobileKey: String,
        observability: LDObservabilityOptions,
        replay: LDSessionReplayOptions,
        observabilityVersion: String,
        callback: (Result<LDStartResult>) -> Unit,
    ) {
        // Mirrors the RN adapter: `LDObserve.init` (which runs OpenTelemetryRum.build())
        // requires the main thread, and consecutive starts get naturally serialized
        // when posted through the main looper.
        Handler(Looper.getMainLooper()).post {
            try {
                doStart(mobileKey, observability, replay, observabilityVersion)
                callback(Result.success(LDStartResult(nativeVersion = observabilityVersion)))
            } catch (t: Throwable) {
                callback(Result.failure(t))
            }
        }
    }

    private fun doStart(
        mobileKey: String,
        observability: LDObservabilityOptions,
        replay: LDSessionReplayOptions,
        observabilityVersion: String,
    ) {
        val resourceAttributes = buildResourceAttributes(
            observability.attributes,
            observabilityVersion,
        )

        // Drives both `analytics.taps` (publish `click` spans) and `instrumentations.userTaps`
        // (run tap detection: window wrapping + hit-testing) from the single Flutter flag.
        val tapsEnabled = observability.analytics?.taps ?: true

        val nativeObservabilityOptions = ObservabilityOptions(
            enabled = observability.isEnabled ?: true,
            serviceName = observability.serviceName ?: DEFAULT_SERVICE_NAME,
            serviceVersion = observability.serviceVersion ?: hostAppVersion(),
            contextFriendlyName = observability.contextFriendlyName,
            resourceAttributes = resourceAttributes,
            customHeaders = observability.customHeaders ?: emptyMap(),
            sessionBackgroundTimeout = observability.sessionBackgroundTimeoutMillis
                ?.milliseconds ?: 15.minutes,
            debug = false,
            otlpEndpoint = observability.otlpEndpoint ?: DEFAULT_OTLP_ENDPOINT,
            backendUrl = observability.backendUrl ?: DEFAULT_BACKEND_URL,
            logsApiLevel = mapLogLevel(observability.logsApiLevel?.toInt()),
            tracesApi = ObservabilityOptions.TracesApi(
                includeErrors = observability.traces?.includeErrors ?: true,
                includeSpans = observability.traces?.includeSpans ?: true,
            ),
            metricsApi = if (observability.metricsEnabled ?: true) {
                ObservabilityOptions.MetricsApi.enabled()
            } else {
                ObservabilityOptions.MetricsApi.disabled()
            },
            analytics = ObservabilityOptions.Analytics(
                taps = tapsEnabled,
                // The Flutter-facing `views` option maps to the native `screenViews`
                // (`screen_view` spans); the legacy OpenTelemetry activity-lifecycle spans it used
                // to control have been removed.
                screenViews = observability.analytics?.views ?: true,
                trackEvents = observability.analytics?.trackEvents ?: true,
                appLifecycle = observability.analytics?.appLifecycle ?: true,
                appLaunch = observability.analytics?.appLaunch ?: true,
            ),
            instrumentations = ObservabilityOptions.Instrumentations(
                crashReporting = observability.instrumentation?.crashReporting ?: true,
                launchTime = observability.instrumentation?.launchTimes ?: true,
                // The Flutter API exposes a single `analytics.taps` flag, but natively taps need two
                // switches: `instrumentation.userTaps` runs the tap-detection machinery (window
                // wrapping + hit-testing) and `analytics.taps` publishes each detected tap as a
                // `click` span. Drive both from the one Flutter flag - matching iOS - so disabling
                // taps stops detection too, instead of leaving the invasive capture running.
                userTaps = tapsEnabled,
                // Flutter navigation is reported explicitly through trackScreenView. Activity
                // lifecycle detection only sees the single host Activity and would overwrite the
                // active Flutter route after a foreground/resume or session reseed.
                screens = false,
            ),
        )

        val privacy = replay.privacy
        val maskTextInputs = privacy?.maskTextInputs ?: true
        // Resolve once so the Flutter capture resolution and the exporter scale
        // stay in sync (a mismatch produces oversized replay frames). Treat a
        // missing or non-positive scale as the 1.0 default; otherwise the exporter
        // would record an invalid scale while the Dart capture falls back to the
        // device pixel ratio, reintroducing the mismatch.
        val replayScale = replay.scale?.takeIf { it > 0 } ?: 1.0
        val nativeReplayOptions = ReplayOptions(
            enabled = replay.isEnabled ?: true,
            sampleRate = replay.sampleRate ?: 1.0,
            frameRate = replay.frameRate ?: 1.0,
            scale = replayScale,
            imageQuality = replay.imageQuality ?: 0.3,
            privacyProfile = PrivacyProfile(
                maskTextInputs = maskTextInputs,
                maskText = privacy?.maskLabels ?: false,
                maskImageViews = privacy?.maskImages ?: false,
                maskWebViews = privacy?.maskWebViews ?: false,
                minimumAlpha = (privacy?.minimumAlpha ?: 0.02).toFloat(),
            ),
        )

        val ldContext = LDObserveContext.builder(LDObserveContext.DEFAULT_KIND, "flutter-user-key")
            .anonymous(true)
            .build()

        LDObserve.init(
            application = application,
            mobileKey = mobileKey,
            ldContext = ldContext,
            observability = nativeObservabilityOptions,
            replay = nativeReplayOptions,
            imageCaptureService = FlutterImageCaptureService(
                channel = captureChannel,
                maskTextInputs = maskTextInputs,
                maskLabels = privacy?.maskLabels ?: false,
                maskImages = privacy?.maskImages ?: false,
                maskWebViews = privacy?.maskWebViews ?: false,
                minimumAlpha = privacy?.minimumAlpha ?: 0.02,
                scale = replayScale,
            ),
        )

        // The Flutter plugin attaches after the host Activity is already created,
        // so the LD session replay SDK missed onActivityCreated() for it. Manually
        // register it so the view tree starts being captured — without this, the
        // session replay shows a black screen.
        activity?.let { LDReplay.registerActivity(it) }
    }

    // Re-creates each Dart span on the native tracer so the native pipeline
    // stamps `session.id` (and applies sampling/batching). Mirrors MAUI's
    // `TraceBuilderAdapter`.
    override fun exportSpans(spans: List<LDSpanData>) {
        val tracer = LDObserveBridge.getKotlinTracer() ?: return
        for (span in spans) {
            val builder = tracer.spanBuilder(
                span.name ?: "",
                span.startTimeSeconds ?: 0.0,
                span.traceId ?: "",
                span.spanId ?: "",
                span.parentSpanId ?: "",
            )

            span.attributes?.let { builder.setAttributes(it) }

            span.events?.forEach { event ->
                if (event == null) return@forEach
                val name = event.name ?: ""
                val attrs = event.attributes
                if (attrs.isNullOrEmpty()) {
                    builder.addEvent(name)
                } else {
                    builder.addEvent(name, attrs)
                }
            }

            val status = span.statusCode?.toInt() ?: 0
            if (status != 0) {
                builder.setStatus(status)
            }

            builder.end(span.endTimeSeconds ?: span.startTimeSeconds ?: 0.0)
        }
    }

    // Forwards the log to the native customer logger so it is emitted as a real
    // `LogRecord` with `session.id` and trace/span correlation.
    override fun recordLog(log: LDLogRecord) {
        val logger = LDObserveBridge.getKotlinLogger() ?: return
        logger.recordLog(
            log.message ?: "",
            log.severityNumber?.toInt() ?: 9,
            log.traceId,
            log.spanId,
            false,
            log.attributes,
        )
    }

    // Forwards a custom track event to the native observability SDK. Native
    // `track` always broadcasts a Session Replay `Track` timeline event and, when
    // `analytics.trackEvents` is enabled, emits the `track` span. The Dart side
    // routes mobile track here instead of through `exportSpans` because only the
    // native track path reaches Session Replay. `contextKeys` (the LD evaluation
    // context's kind -> key pairs) is applied to the span so it is attributed to
    // the same context the web SDK records. The bridge owns the `Map` -> `LDValue`
    // conversion so this plugin stays independent of the LaunchDarkly
    // feature-flag SDK.
    override fun track(
        key: String,
        data: Map<String, Any?>?,
        metricValue: Double?,
        contextKeys: Map<String, String>?,
    ) {
        LDObserveBridge.track(key, data, metricValue, contextKeys)
    }

    // Forwards an identify to the native observability SDK (which caches the
    // context keys so the manual track path is attributed to the active context)
    // and Session Replay (which records who the user is on the active recording).
    // The Dart side calls this from the LaunchDarkly client's `afterIdentify`
    // hook. Reuses the same public cross-platform hook proxies that React Native
    // (`LDReplay.hookProxy`) and MAUI drive, mirroring the iOS plugin — so no
    // bespoke combined bridge entry point is needed.
    override fun identify(
        contextKeys: Map<String, String>,
        canonicalKey: String,
        completed: Boolean,
    ) {
        LDObserveBridge.getObservabilityHookProxy()
            ?.afterIdentify(contextKeys, canonicalKey, completed)
        LDReplay.hookProxy?.afterIdentify(contextKeys, canonicalKey, completed)
    }

    // Forwards a screen view to the native observability SDK, which emits the
    // `screen_view` span and broadcasts a Session Replay `Navigate` timeline
    // event. Native automatic screen detection (Activity lifecycle) never fires
    // for Flutter route changes inside the single host Activity, so the Dart side
    // reports them here (typically from a `NavigatorObserver`).
    override fun trackScreenView(
        name: String,
        screenClass: String?,
        screenId: String?,
        category: String?,
        properties: Map<String, Any?>?,
    ) {
        LDObserve.trackScreenView(name, screenClass, screenId, category, properties)
    }

    // Forwards a click to the native observability SDK, which emits the `click`
    // span and broadcasts a Session Replay `Click` timeline event. Flutter renders
    // its whole UI into one `FlutterSurfaceView`, so native hit-testing can only
    // ever name that view; the Dart side walks the widget tree instead and reports
    // the real target here.
    override fun trackClick(
        id: String?,
        tag: String?,
        classname: String?,
        text: String?,
        xpath: String?,
        screenId: String?,
        x: Long?,
        y: Long?,
        timestampMillis: Long?,
        properties: Map<String, Any?>?,
    ) {
        LDObserve.trackClick(
            id = id,
            tag = tag,
            classname = classname,
            text = text,
            xpath = xpath,
            screenId = screenId,
            x = x?.toInt(),
            y = y?.toInt(),
            timestampMillis = timestampMillis,
            properties = properties,
        )
    }

    // Declares that Dart now resolves clicks for the Flutter view, so native tap
    // detection stops reporting taps that land on it and each tap is counted once.
    // Handshaked from Dart rather than set at init: native starts before the widget
    // tree exists, so an app that never installs Dart click detection keeps the
    // coarse native clicks instead of silently reporting none.
    override fun setEmbedderClickHandling(enabled: Boolean) {
        LDObserve.setEmbedderClickHandling(enabled)
    }

    // Session Replay is the only native component with a teardown; the native
    // observability SDK keeps its automatic instrumentation running.
    override fun shutdown() {
        LDReplay.stop()
        LDReplay.flush()
    }

    /**
     * Maps the OpenTelemetry log-severity number sent across the bridge onto the
     * native [ObservabilityOptions.LogLevel]. Defaults to [ObservabilityOptions.LogLevel.INFO]
     * when null or unrecognized.
     */
    private fun mapLogLevel(severity: Int?): ObservabilityOptions.LogLevel {
        if (severity == null) return ObservabilityOptions.LogLevel.INFO
        return ObservabilityOptions.LogLevel.entries.firstOrNull { it.level == severity }
            ?: ObservabilityOptions.LogLevel.INFO
    }

    // The host app's `versionName`, which Flutter sets from `pubspec.yaml`.
    // Empty rather than a made-up version when the app declares none.
    private fun hostAppVersion(): String = try {
        @Suppress("DEPRECATION")
        application.packageManager
            .getPackageInfo(application.packageName, 0)
            .versionName
            .orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }

    private fun buildResourceAttributes(
        source: Map<String, Any?>?,
        observabilityVersion: String,
    ): Attributes {
        val builder = Attributes.builder()
        source?.forEach { (key, value) ->
            when (value) {
                is String -> builder.put(AttributeKey.stringKey(key), value)
                is Boolean -> builder.put(AttributeKey.booleanKey(key), value)
                is Long -> builder.put(AttributeKey.longKey(key), value)
                is Int -> builder.put(AttributeKey.longKey(key), value.toLong())
                is Double -> builder.put(AttributeKey.doubleKey(key), value)
                is Float -> builder.put(AttributeKey.doubleKey(key), value.toDouble())
                null -> Unit
                else -> builder.put(AttributeKey.stringKey(key), value.toString())
            }
        }
        builder.put("telemetry.distro.name", FLUTTER_DISTRO_NAME)
        builder.put("telemetry.distro.version", observabilityVersion)
        return builder.build()
    }

    companion object {
        private const val FLUTTER_DISTRO_NAME = "observability-flutter-android"
        private const val DEFAULT_SERVICE_NAME = "observability-flutter"
        private const val DEFAULT_OTLP_ENDPOINT =
            "https://otel.observability.app.launchdarkly.com:4318"
        private const val DEFAULT_BACKEND_URL =
            "https://pub.observability.app.launchdarkly.com"
    }
}
