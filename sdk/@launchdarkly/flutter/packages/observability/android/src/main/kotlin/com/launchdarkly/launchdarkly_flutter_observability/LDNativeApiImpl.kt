package com.launchdarkly.launchdarkly_flutter_observability

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import com.launchdarkly.observability.api.ObservabilityOptions
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

        val nativeObservabilityOptions = ObservabilityOptions(
            enabled = observability.isEnabled ?: true,
            serviceName = observability.serviceName ?: DEFAULT_SERVICE_NAME,
            serviceVersion = observability.serviceVersion ?: DEFAULT_SERVICE_VERSION,
            resourceAttributes = resourceAttributes,
            customHeaders = observability.customHeaders ?: emptyMap(),
            sessionBackgroundTimeout = observability.sessionBackgroundTimeoutMillis
                ?.milliseconds ?: 15.minutes,
            debug = false,
            otlpEndpoint = observability.otlpEndpoint ?: DEFAULT_OTLP_ENDPOINT,
            backendUrl = observability.backendUrl ?: DEFAULT_BACKEND_URL,
            logsApiLevel = mapLogLevel(observability.logsApiLevel),
            tracesApi = ObservabilityOptions.TracesApi(
                includeErrors = observability.traces?.includeErrors ?: true,
                includeSpans = observability.traces?.includeSpans ?: true,
            ),
            metricsApi = if (observability.metricsEnabled ?: true) {
                ObservabilityOptions.MetricsApi.enabled()
            } else {
                ObservabilityOptions.MetricsApi.disabled()
            },
            productAnalytics = ObservabilityOptions.ProductAnalytics(
                taps = observability.productAnalytics?.taps ?: false,
                pageViews = observability.productAnalytics?.pageViews ?: true,
                trackEvents = observability.productAnalytics?.trackEvents ?: true,
            ),
            instrumentations = ObservabilityOptions.Instrumentations(
                crashReporting = observability.instrumentation?.crashReporting ?: true,
                launchTime = observability.instrumentation?.launchTimes ?: true,
                activityLifecycle = true,
            ),
        )

        val privacy = replay.privacy
        val maskTextInputs = privacy?.maskTextInputs ?: true
        val nativeReplayOptions = ReplayOptions(
            enabled = replay.isEnabled ?: true,
            frameRate = replay.frameRate ?: 1.0,
            privacyProfile = PrivacyProfile(
                maskTextInputs = maskTextInputs,
                maskText = privacy?.maskLabels ?: false,
                maskImageViews = privacy?.maskImages ?: false,
                maskWebViews = privacy?.maskWebViews ?: false,
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
            ),
        )

        // The Flutter plugin attaches after the host Activity is already created,
        // so the LD session replay SDK missed onActivityCreated() for it. Manually
        // register it so the view tree starts being captured — without this, the
        // session replay shows a black screen.
        activity?.let { LDReplay.registerActivity(it) }
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
        private const val DEFAULT_SERVICE_VERSION = "0.1.0"
        private const val DEFAULT_OTLP_ENDPOINT =
            "https://otel.observability.app.launchdarkly.com:4318"
        private const val DEFAULT_BACKEND_URL =
            "https://pub.observability.app.launchdarkly.com"
    }
}
