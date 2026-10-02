import Foundation
import SkipFuse

#if canImport(LaunchDarkly)
import LaunchDarkly
#endif
#if canImport(LaunchDarklyOtel)
import LaunchDarklyOtel
#endif

/// Cross-platform facade over the official LaunchDarkly mobile SDKs.
///
/// This is the pattern to copy when adding LaunchDarkly to a Skip app: shared SwiftUI
/// code calls only this type, and each method funnels into exactly one platform branch.
///
/// | | Flags | Observability |
/// | --- | --- | --- |
/// | iOS / macOS | [`ios-client-sdk`](https://github.com/launchdarkly/ios-client-sdk) | `LaunchDarklyOtel` (manual signals only) |
/// | Android | [`launchdarkly-android-client-sdk`](https://github.com/launchdarkly/android-client-sdk) | `launchdarkly-observability-android` (automatic instrumentation) |
///
/// Three compilation conditions do the platform routing, and each means something
/// different:
///
/// - `#if os(Android)` — native Swift compiled for Android. Calls the Kotlin SDK through
///   the ``LaunchDarklyAndroid`` helper at the bottom of this file.
/// - `#elseif canImport(LaunchDarkly)` — Apple platforms, calling the Swift SDK directly.
/// - `#if SKIP` — the block Skip transpiles to Kotlin. It is the only place Kotlin/Java
///   types may appear.
///
/// Telemetry is recorded through ``LaunchDarklyObserve``.
public enum LaunchDarklyFlags {

    // MARK: - Lifecycle

    /// Starts the platform LaunchDarkly client.
    ///
    /// Call this once, at app launch. Both platforms return immediately and serve cached
    /// flag values until the first sync completes, so it is safe to call from
    /// `application(_:didFinishLaunchingWithOptions:)` or an `App` initializer.
    public static func start(
        mobileKey: String = LaunchDarklyConfig.mobileKey,
        contextKey: String = LaunchDarklyConfig.contextKey
    ) {
        guard isPlaceholder(mobileKey) == false else {
            logger.warning("LaunchDarkly: mobile key not set — edit LaunchDarklyConfig.mobileKey")
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
        guard let context = makeContext(key: contextKey) else { return }

        LDClient.start(
            config: makeConfig(mobileKey: mobileKey),
            context: context,
            startWaitSeconds: startWaitSeconds
        ) { timedOut in
            if timedOut {
                logger.warning("LaunchDarkly: started with cached flags; sync still in flight")
            } else {
                logger.info("LaunchDarkly: client ready")
            }
        }
        #endif
    }

    /// Switches the client to a different evaluation context.
    ///
    /// Subsequent variation and ``track(_:data:metricValue:)`` calls are attributed to
    /// this context. The SDK's `DedupingHook` also clears its exposure cache once
    /// identify completes, so the next evaluation of each flag is reported again.
    public static func identify(contextKey: String) {
        #if os(Android)
        LaunchDarklyAndroid.identify(contextKey: contextKey)
        #elseif canImport(LaunchDarkly)
        guard let context = makeContext(key: contextKey) else { return }
        LDClient.get()?.identify(context: context) { _ in }
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

    /// Evaluates a JSON flag as a native dictionary, falling back to `defaultValue` when
    /// the client is unavailable or the flag value is not a JSON object.
    ///
    /// The dictionary crosses the Android bridge as JSON text, so values must be
    /// JSON-representable (`String`, numbers, `Bool`, arrays, nested dictionaries, `NSNull`).
    public static func jsonVariation(_ key: String, defaultValue: [String: Any]) -> [String: Any] {
        #if os(Android)
        let json = LaunchDarklyAndroid.jsonVariation(
            key,
            defaultJson: JSONBridge.string(from: defaultValue)
        )
        return JSONBridge.dictionary(from: json) ?? defaultValue
        #elseif canImport(LaunchDarkly)
        guard let client = LDClient.get() else { return defaultValue }
        let value = client.jsonVariation(forKey: key, defaultValue: LDValue.fromFoundation(defaultValue))
        return (value.toFoundation() as? [String: Any]) ?? defaultValue
        #else
        return defaultValue
        #endif
    }

    // MARK: - Events

    /// Records a custom analytics event.
    ///
    /// The observability plugin also turns this into a `track` span through its
    /// `afterTrack` hook.
    ///
    /// Keep `data` flat and low cardinality: metric filters do not read nested objects or
    /// arrays. Pass `metricValue` for numeric metrics. Values must be JSON-representable
    /// so they survive the Android bridge.
    public static func track(
        _ key: String,
        data: [String: Any]? = nil,
        metricValue: Double? = nil
    ) {
        #if os(Android)
        LaunchDarklyAndroid.track(
            key,
            dataJson: data.map(JSONBridge.string(from:)),
            metricValue: metricValue
        )
        #elseif canImport(LaunchDarkly)
        LDClient.get()?.track(
            key: key,
            data: data.map { LDValue.fromFoundation($0) },
            metricValue: metricValue
        )
        #endif
    }

    /// Flushes buffered analytics events immediately.
    ///
    /// Events otherwise sit in the buffer for up to 30 seconds, which is exactly the
    /// window lost if the app is killed or backgrounded right after a user-visible error.
    public static func flush() {
        #if os(Android)
        LaunchDarklyAndroid.flush()
        #elseif canImport(LaunchDarkly)
        LDClient.get()?.flush()
        #endif
    }

    // MARK: - Apple platform helpers

    /// How long the start completion waits for the first flag payload before reporting a
    /// timeout. The client is usable either way; this only decides when the callback runs.
    private static let startWaitSeconds: TimeInterval = 5

    private static func isPlaceholder(_ mobileKey: String) -> Bool {
        mobileKey.isEmpty || mobileKey == "YOUR_MOBILE_KEY"
    }

    #if canImport(LaunchDarkly)
    private static func makeConfig(mobileKey: String) -> LDConfig {
        var config = LDConfig(mobileKey: mobileKey, autoEnvAttributes: .enabled)
        #if canImport(LaunchDarklyOtel)
        // The OTel-only product installs no instrumentation of its own: no URLSession or
        // UIKit swizzling, no crash handlers, no periodic metrics. Every signal this app
        // reports comes from an explicit LaunchDarklyObserve call, which is what makes it
        // safe to run alongside another observability SDK.
        config.plugins = [
            Otel(options: ObservabilityOptions(
                serviceName: LaunchDarklyConfig.serviceName,
                serviceVersion: LaunchDarklyConfig.serviceVersion,
                resourceAttributes: ["app.platform": .string("skip-fuse-ios")],
                isDebug: true
            ))
        ]
        // DedupingHook reports each distinct evaluation result once per window (10 minutes
        // by default) rather than on every variation call. LaunchDarkly's own feature,
        // debug, and summary events are unaffected, so evaluation counts do not change.
        config.hooks = [DedupingHook(ExposureHook())]
        #endif
        return config
    }

    /// Builds the single-kind `user` context both platforms evaluate against.
    private static func makeContext(key: String) -> LDContext? {
        var builder = LDContextBuilder(key: key)
        builder.kind("user")

        switch builder.build() {
        case .success(let context):
            return context
        case .failure(let error):
            logger.error("LaunchDarkly: could not build context '\(key)': \(error)")
            return nil
        }
    }
    #endif
}

// MARK: - Android (transpiled to Kotlin by Skip)

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

/// Kotlin-side LaunchDarkly access, bridged into native Fuse Swift on Android.
///
/// Everything here is transpiled, so it may use Kotlin and Java types freely. Only
/// bridgeable types (scalars, `String`, optionals) may appear in the signatures that
/// ``LaunchDarklyFlags`` calls — JSON values therefore cross as text.
public enum LaunchDarklyAndroid {
    public static func start(mobileKey: String, contextKey: String, serviceName: String, serviceVersion: String) {
        let application = ProcessInfo.processInfo.androidContext.getApplicationContext() as android.app.Application

        // The full observability product, with its automatic instrumentation left at the
        // defaults: app launch, lifecycle, screen view, click, and network spans.
        let observability = Observability(
            application,
            mobileKey,
            ObservabilityOptions(serviceName: serviceName, serviceVersion: serviceVersion, debug: true)
        )

        let config = LDConfig.Builder(AutoEnvAttributes.Enabled)
            .mobileKey(mobileKey)
            .plugins(Components.plugins().setPlugins(listOf(observability)))
            // Matches the Apple side: dedupe exposures, leave evaluation counts alone.
            .hooks(Components.hooks().addHook(DedupingHook(ExposureHook())))
            .build()

        // `LDContext.create` produces a single-kind `user` context, the same shape
        // `LaunchDarklyFlags.makeContext(key:)` builds on Apple platforms.
        let context = LDContext.create(contextKey)

        // A zero timeout returns immediately and serves cached flags while the first sync
        // runs in the background.
        // Skip strips `.init` assuming a constructor call, so the Kotlin form is spelled out here.
        // SKIP REPLACE: LDClient.init(application, config, context, 0)
        LDClient.init(application, config, context, 0)
    }

    public static func identify(contextKey: String) {
        guard let client = client() else { return }
        client.identify(LDContext.create(contextKey))
    }

    public static func boolVariation(_ key: String, defaultValue: Bool) -> Bool {
        guard let client = client() else { return defaultValue }
        return client.boolVariation(key, defaultValue)
    }

    public static func intVariation(_ key: String, defaultValue: Int) -> Int {
        guard let client = client() else { return defaultValue }
        return client.intVariation(key, defaultValue)
    }

    public static func doubleVariation(_ key: String, defaultValue: Double) -> Double {
        guard let client = client() else { return defaultValue }
        return client.doubleVariation(key, defaultValue)
    }

    public static func stringVariation(_ key: String, defaultValue: String) -> String {
        guard let client = client() else { return defaultValue }
        return client.stringVariation(key, defaultValue)
    }

    public static func jsonVariation(_ key: String, defaultJson: String) -> String {
        guard let client = client() else { return defaultJson }
        return client.jsonValueVariation(key, LDValue.parse(defaultJson)).toJsonString()
    }

    public static func track(_ key: String, dataJson: String?, metricValue: Double?) {
        guard let client = client() else { return }
        let data = dataJson.map { LDValue.parse($0) } ?? LDValue.ofNull()

        if let metricValue = metricValue {
            client.trackMetric(key, data, metricValue)
        } else if dataJson == nil {
            client.track(key)
        } else {
            client.trackData(key, data)
        }
    }

    public static func flush() {
        guard let client = client() else { return }
        client.flush()
    }

    /// `LDClient.get()` throws until the client has been initialized, so every call site
    /// degrades to the caller's default rather than trapping.
    private static func client() -> LDClient? {
        return try? LDClient.get()
    }
}
#endif
