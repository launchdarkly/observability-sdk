import Foundation
import SkipFuse

#if canImport(LaunchDarklyOtel)
import LaunchDarklyOtel
#endif

/// Thin dual-platform wrapper over the LaunchDarkly observability SDKs' `LDObserve` API.
///
/// - **iOS:** `LaunchDarklyOtel` — the OTel-only product. Nothing is captured
///   automatically, so every signal in this app comes from a call below.
/// - **Android:** `com.launchdarkly:launchdarkly-observability-android` with its
///   default automatic instrumentation, reached through a `#if SKIP` Kotlin helper.
///
/// `properties` are plain native dictionaries. On Android they cross the bridge as
/// JSON, so values must be JSON-representable and are treated as a flat map of
/// scalars — nested containers are recorded as their JSON form.
public enum LaunchDarklyObserve {
    /// Log severity, using the OpenTelemetry severity numbers shared by both platforms.
    public enum LogSeverity: Int, CaseIterable {
        case trace = 1
        case debug = 5
        case info = 9
        case warn = 13
        case error = 17
        case fatal = 21
    }

    /// Records a log record on the OpenTelemetry logs pipeline.
    public static func recordLog(
        _ message: String,
        severity: LogSeverity = .info,
        properties: [String: Any] = [:]
    ) {
        #if os(Android)
        LaunchDarklyObserveAndroid.recordLog(
            message: message,
            severityNumber: severity.rawValue,
            propertiesJson: JSONBridge.string(from: properties)
        )
        #elseif canImport(LaunchDarklyOtel)
        LDObserve.shared.recordLog(
            message: message,
            severity: Severity(rawValue: severity.rawValue) ?? .info,
            properties: properties
        )
        #endif
    }

    /// Records an error as an exception span, with an optional cause description.
    public static func recordError(_ message: String, cause: String? = nil) {
        #if os(Android)
        LaunchDarklyObserveAndroid.recordError(message: message, cause: cause)
        #elseif canImport(LaunchDarklyOtel)
        // The bridge builds the same message/cause error shape the Android SDK records.
        ObjcLDObserveBridge.recordError(message: message, cause: cause)
        #endif
    }

    /// Runs `body` inside a span named `name` and returns its result.
    public static func recordSpan<T>(
        _ name: String,
        properties: [String: Any] = [:],
        _ body: () -> T
    ) -> T {
        #if os(Android)
        // Spans stay on the Kotlin side; only an integer handle crosses the bridge.
        let handle = LaunchDarklyObserveAndroid.startSpan(
            name: name,
            propertiesJson: JSONBridge.string(from: properties)
        )
        defer { LaunchDarklyObserveAndroid.endSpan(handle: handle) }
        return body()
        #elseif canImport(LaunchDarklyOtel)
        let span = LDObserve.shared.startSpan(name: name, properties: properties)
        defer { span.end() }
        return body()
        #else
        return body()
        #endif
    }

    /// Records a gauge-style metric.
    public static func recordMetric(_ name: String, value: Double) {
        #if os(Android)
        LaunchDarklyObserveAndroid.recordMetric(name: name, value: value)
        #elseif canImport(LaunchDarklyOtel)
        LDObserve.shared.recordMetric(metric: Metric(name: name, value: value))
        #endif
    }

    /// Records a counter metric.
    public static func recordCount(_ name: String, value: Double) {
        #if os(Android)
        LaunchDarklyObserveAndroid.recordCount(name: name, value: value)
        #elseif canImport(LaunchDarklyOtel)
        LDObserve.shared.recordCount(metric: Metric(name: name, value: value))
        #endif
    }

    /// Increments a counter metric.
    public static func recordIncr(_ name: String, value: Double = 1) {
        #if os(Android)
        LaunchDarklyObserveAndroid.recordIncr(name: name, value: value)
        #elseif canImport(LaunchDarklyOtel)
        LDObserve.shared.recordIncr(metric: Metric(name: name, value: value))
        #endif
    }

    /// Records a histogram sample.
    public static func recordHistogram(_ name: String, value: Double) {
        #if os(Android)
        LaunchDarklyObserveAndroid.recordHistogram(name: name, value: value)
        #elseif canImport(LaunchDarklyOtel)
        LDObserve.shared.recordHistogram(metric: Metric(name: name, value: value))
        #endif
    }

    /// Records an up/down counter delta.
    public static func recordUpDownCounter(_ name: String, value: Double) {
        #if os(Android)
        LaunchDarklyObserveAndroid.recordUpDownCounter(name: name, value: value)
        #elseif canImport(LaunchDarklyOtel)
        LDObserve.shared.recordUpDownCounter(metric: Metric(name: name, value: value))
        #endif
    }

    /// Records a screen view. Android also detects screens automatically; on iOS this
    /// is the only way a `screen_view` span is produced.
    public static func trackScreenView(_ name: String, properties: [String: Any] = [:]) {
        #if os(Android)
        LaunchDarklyObserveAndroid.trackScreenView(
            name: name,
            propertiesJson: JSONBridge.string(from: properties)
        )
        #elseif canImport(LaunchDarklyOtel)
        LDObserve.shared.trackScreenView(name: name, category: nil, properties: properties)
        #endif
    }
}

// MARK: - Android (transpiled / bridged)

#if SKIP
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.sdk.LDObserve

/// Kotlin-side `LDObserve` access. Bridged into Fuse native Swift on Android.
///
/// Only bridgeable scalar types cross this boundary; property maps arrive as JSON
/// and are rebuilt as a `java.util.HashMap` here.
public enum LaunchDarklyObserveAndroid {
    private static var spans: [Int: io.opentelemetry.api.trace.Span] = [:]
    private static var nextSpanHandle = 0

    public static func recordLog(message: String, severityNumber: Int, propertiesJson: String) {
        LDObserve.recordLog(message, severityNumber, properties(from: propertiesJson))
    }

    public static func recordError(message: String, cause: String?) {
        LDObserve.recordError(message, cause ?? "")
    }

    public static func startSpan(name: String, propertiesJson: String) -> Int {
        let span = LDObserve.startSpan(name, properties(from: propertiesJson))
        nextSpanHandle += 1
        spans[nextSpanHandle] = span
        return nextSpanHandle
    }

    public static func endSpan(handle: Int) {
        guard let span = spans.removeValue(forKey: handle) else { return }
        span.end()
    }

    public static func recordExposure(flagKey: String, variation: Int, value: String) {
        let attributes = java.util.HashMap<String, Any>()
        attributes.put("flag_key", flagKey)
        attributes.put("variation", variation)
        attributes.put("value", value)
        let span = LDObserve.startSpan("flag_exposure", attributes)
        span.end()
    }

    public static func recordMetric(name: String, value: Double) {
        LDObserve.recordMetric(Metric(name, value))
    }

    public static func recordCount(name: String, value: Double) {
        LDObserve.recordCount(Metric(name, value))
    }

    public static func recordIncr(name: String, value: Double) {
        LDObserve.recordIncr(Metric(name, value))
    }

    public static func recordHistogram(name: String, value: Double) {
        LDObserve.recordHistogram(Metric(name, value))
    }

    public static func recordUpDownCounter(name: String, value: Double) {
        LDObserve.recordUpDownCounter(Metric(name, value))
    }

    public static func trackScreenView(name: String, propertiesJson: String) {
        LDObserve.trackScreenView(name, nil, nil, nil, properties(from: propertiesJson))
    }

    private static func properties(from json: String) -> java.util.HashMap<String, Any> {
        let map = java.util.HashMap<String, Any>()
        guard json != "{}", !json.isEmpty else { return map }
        let object = org.json.JSONObject(json)
        let keys = object.keys()
        while keys.hasNext() {
            let key = keys.next()
            map.put(key, object.get(key))
        }
        return map
    }
}
#endif
