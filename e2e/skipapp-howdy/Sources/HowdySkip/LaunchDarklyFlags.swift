import Foundation
import SkipFuse

#if canImport(LaunchDarkly)
import LaunchDarkly
#endif
#if canImport(LaunchDarklyOtel)
import LaunchDarklyOtel
#endif

/// Thin dual-platform wrapper over the official LaunchDarkly mobile SDKs.
///
/// - **iOS / macOS:** [`ios-client-sdk`](https://github.com/launchdarkly/ios-client-sdk),
///   with the OTel-only `LaunchDarklyOtel` observability plugin (manual signals only)
/// - **Android:** [`launchdarkly-android-client-sdk`](https://github.com/launchdarkly/android-client-sdk),
///   with the full `launchdarkly-observability-android` plugin (default automatic
///   instrumentation), reached through a `#if SKIP` Kotlin helper that is bridged
///   into native Fuse Swift
///
/// Shared SwiftUI code should call only this type — never the platform SDKs directly.
/// Telemetry is recorded through ``LaunchDarklyObserve``.
public enum LaunchDarklyFlags {
    /// Starts the platform LaunchDarkly client. Safe to call once at app launch.
    public static func start(
        mobileKey: String = LaunchDarklyConfig.mobileKey,
        contextKey: String = LaunchDarklyConfig.contextKey
    ) {
        guard mobileKey != "YOUR_MOBILE_KEY", !mobileKey.isEmpty else {
            logger.warning("LaunchDarkly mobile key not set — edit LaunchDarklyConfig.mobileKey")
            return
        }

        #if os(Android)
        LaunchDarklyAndroid.start(
            mobileKey: mobileKey,
            contextKey: contextKey,
            serviceName: LaunchDarklyConfig.serviceName,
            serviceVersion: LaunchDarklyConfig.serviceVersion
        )
        #elseif canImport(LaunchDarkly)
        startIOS(mobileKey: mobileKey, contextKey: contextKey)
        #endif
    }

    // MARK: - Flag evaluation

    /// Evaluates a boolean flag, falling back to `defaultValue` when the client is unavailable.
    public static func boolVariation(_ key: String, defaultValue: Bool) -> Bool {
        #if os(Android)
        return LaunchDarklyAndroid.boolVariation(key, defaultValue: defaultValue)
        #elseif canImport(LaunchDarkly)
        return LDClient.get()?.boolVariation(forKey: key, defaultValue: defaultValue) ?? defaultValue
        #else
        return defaultValue
        #endif
    }

    /// Evaluates an integer flag, falling back to `defaultValue` when the client is unavailable.
    public static func intVariation(_ key: String, defaultValue: Int) -> Int {
        #if os(Android)
        return LaunchDarklyAndroid.intVariation(key, defaultValue: defaultValue)
        #elseif canImport(LaunchDarkly)
        return LDClient.get()?.intVariation(forKey: key, defaultValue: defaultValue) ?? defaultValue
        #else
        return defaultValue
        #endif
    }

    /// Evaluates a double flag, falling back to `defaultValue` when the client is unavailable.
    public static func doubleVariation(_ key: String, defaultValue: Double) -> Double {
        #if os(Android)
        return LaunchDarklyAndroid.doubleVariation(key, defaultValue: defaultValue)
        #elseif canImport(LaunchDarkly)
        return LDClient.get()?.doubleVariation(forKey: key, defaultValue: defaultValue) ?? defaultValue
        #else
        return defaultValue
        #endif
    }

    /// Evaluates a string flag, falling back to `defaultValue` when the client is unavailable.
    public static func stringVariation(_ key: String, defaultValue: String) -> String {
        #if os(Android)
        return LaunchDarklyAndroid.stringVariation(key, defaultValue: defaultValue)
        #elseif canImport(LaunchDarkly)
        return LDClient.get()?.stringVariation(forKey: key, defaultValue: defaultValue) ?? defaultValue
        #else
        return defaultValue
        #endif
    }

    /// Evaluates a JSON flag as a native dictionary, falling back to `defaultValue` when the
    /// client is unavailable or the flag value is not a JSON object.
    ///
    /// The dictionary crosses the Android bridge as a JSON string, so values must be
    /// JSON-representable (`String`, numbers, `Bool`, arrays, nested dictionaries, `NSNull`).
    public static func jsonVariation(_ key: String, defaultValue: [String: Any]) -> [String: Any] {
        #if os(Android)
        let json = LaunchDarklyAndroid.jsonVariation(key, defaultJson: JSONBridge.string(from: defaultValue))
        return JSONBridge.dictionary(from: json) ?? defaultValue
        #elseif canImport(LaunchDarkly) && canImport(LaunchDarklyOtel)
        guard let client = LDClient.get() else { return defaultValue }
        let value = client.jsonVariation(forKey: key, defaultValue: LDValue.fromFoundation(defaultValue))
        return (value.toFoundation() as? [String: Any]) ?? defaultValue
        #else
        return defaultValue
        #endif
    }

    /// Records a custom analytics event with the LaunchDarkly client. The observability
    /// plugin also turns this into a `track` span through its `afterTrack` hook.
    ///
    /// Pass a flat `data` dictionary for metric filters (as in the guarded-rollout
    /// `app-error` example). Values must be JSON-representable on Android.
    public static func track(
        _ key: String,
        data: [String: Any]? = nil,
        metricValue: Double? = nil
    ) {
        #if os(Android)
        LaunchDarklyAndroid.track(
            key,
            dataJson: data.map(JSONBridge.string(from:)) ?? "{}",
            metricValue: metricValue
        )
        #elseif canImport(LaunchDarkly)
        let ldData = data.map { LDValue.fromFoundation($0) }
        LDClient.get()?.track(key: key, data: ldData, metricValue: metricValue)
        #endif
    }

    /// Flushes the SDK event buffer immediately.
    ///
    /// Call this after tracking a user-visible error so the event is not left sitting
    /// for up to 30 seconds if the app is killed or backgrounded.
    public static func flush() {
        #if os(Android)
        LaunchDarklyAndroid.flush()
        #elseif canImport(LaunchDarkly)
        LDClient.get()?.flush()
        #endif
    }

    /// Changes the client's current evaluation context.
    ///
    /// Subsequent variation and track calls use this context. SDK-provided
    /// `DedupingHook` also clears its exposure cache after identify completes.
    public static func identify(contextKey: String) {
        #if os(Android)
        LaunchDarklyAndroid.identify(contextKey: contextKey)
        #elseif canImport(LaunchDarkly)
        var builder = LDContextBuilder(key: contextKey)
        builder.kind("user")
        guard let context = try? builder.build().get() else { return }
        LDClient.get()?.identify(context: context) { _ in }
        #endif
    }

    #if canImport(LaunchDarkly)
    private static func startIOS(mobileKey: String, contextKey: String) {
        var config = LDConfig(mobileKey: mobileKey, autoEnvAttributes: .enabled)
        #if canImport(LaunchDarklyOtel)
        // OTel-only product: no swizzling, no crash reporting, no periodic metrics.
        // Everything reported from this app is recorded explicitly via LaunchDarklyObserve.
        config.plugins = [
            Otel(options: ObservabilityOptions(
                serviceName: LaunchDarklyConfig.serviceName,
                serviceVersion: LaunchDarklyConfig.serviceVersion,
                resourceAttributes: ["app.platform": .string("skip-fuse-ios")],
                isDebug: true
            ))
        ]
        // Each per-key variation produces a deduplicated `flag_exposure` span.
        // This does not affect LaunchDarkly's own evaluation events or counts.
        config.hooks = [DedupingHook(ExposureHook())]
        #endif
        var builder = LDContextBuilder(key: contextKey)
        builder.kind("user")
        guard let context = try? builder.build().get() else {
            logger.error("LaunchDarkly: failed to build LDContext")
            return
        }
        LDClient.start(config: config, context: context, startWaitSeconds: 5) { timedOut in
            if timedOut {
                logger.warning("LaunchDarkly: started without the latest flags")
            } else {
                logger.info("LaunchDarkly: client ready")
            }
        }
    }
    #endif
}

// MARK: - Android (transpiled / bridged)

#if SKIP
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import com.launchdarkly.sdk.android.LDConfig.Builder.AutoEnvAttributes
import com.launchdarkly.sdk.android.integrations.DedupingHook
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.plugin.Observability

/// Kotlin-side LaunchDarkly access. Bridged into Fuse native Swift on Android.
public enum LaunchDarklyAndroid {
    public static func start(mobileKey: String, contextKey: String, serviceName: String, serviceVersion: String) {
        let application = ProcessInfo.processInfo.androidContext.getApplicationContext() as android.app.Application
        // Full observability plugin: automatic instrumentation left at its defaults.
        let observability = Observability(
            application,
            mobileKey,
            ObservabilityOptions(serviceName: serviceName, serviceVersion: serviceVersion, debug: true)
        )
        let config = LDConfig.Builder(AutoEnvAttributes.Enabled)
            .mobileKey(mobileKey)
            .plugins(Components.plugins().setPlugins(listOf(observability)))
            .hooks(Components.hooks().addHook(DedupingHook(ExposureHook())))
            .build()
        let context = LDContext.create(contextKey)
        // Non-blocking init: use cached flags immediately; refresh in the background.
        // Skip strips `.init` assuming a constructor call, so the Kotlin form is spelled out here.
        // SKIP REPLACE: LDClient.init(application, config, context, 0)
        LDClient.init(application, config, context, 0)
        android.util.Log.i("LaunchDarkly", "Android client started with observability plugin")
    }

    public static func boolVariation(_ key: String, defaultValue: Bool) -> Bool {
        guard let client = try? LDClient.get() else { return defaultValue }
        let detail = client.boolVariationDetail(key, defaultValue)
        android.util.Log.i("LaunchDarkly", "\(key)=\(detail.getValue()) reason=\(detail.getReason())")
        return detail.getValue()
    }

    public static func intVariation(_ key: String, defaultValue: Int) -> Int {
        guard let client = try? LDClient.get() else { return defaultValue }
        return client.intVariation(key, defaultValue)
    }

    public static func doubleVariation(_ key: String, defaultValue: Double) -> Double {
        guard let client = try? LDClient.get() else { return defaultValue }
        return client.doubleVariation(key, defaultValue)
    }

    public static func stringVariation(_ key: String, defaultValue: String) -> String {
        guard let client = try? LDClient.get() else { return defaultValue }
        return client.stringVariation(key, defaultValue)
    }

    public static func jsonVariation(_ key: String, defaultJson: String) -> String {
        guard let client = try? LDClient.get() else { return defaultJson }
        return client.jsonValueVariation(key, LDValue.parse(defaultJson)).toJsonString()
    }

    public static func track(_ key: String, dataJson: String, metricValue: Double?) {
        guard let client = try? LDClient.get() else { return }
        let data = LDValue.parse(dataJson)
        if let metricValue = metricValue {
            client.trackMetric(key, data, metricValue)
        } else if dataJson == "{}" {
            client.track(key)
        } else {
            client.trackData(key, data)
        }
    }

    public static func flush() {
        guard let client = try? LDClient.get() else { return }
        client.flush()
    }

    public static func identify(contextKey: String) {
        guard let client = try? LDClient.get() else { return }
        client.identify(LDContext.create(contextKey))
    }
}
#endif
