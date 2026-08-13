/// Configuration for the LaunchDarkly Flag SDK demo in this sample.
///
/// Replace `mobileKey` with a client-side mobile key from your LaunchDarkly project
/// (Account settings → Projects → … → Environments → Mobile key). Create a boolean
/// flag named ``featureFlagKey`` (or change the key to match an existing flag).
public enum LaunchDarklyConfig {
    /// Client-side mobile key. Do not commit production secrets to a public fork.
    /// Reused from swift-launchdarkly-observability/TestAppShared/Secrets.xcconfig.
    /// The staging keys in that file only work against the `ld-stg` service endpoints,
    /// so this sample uses the production key with the SDKs' default endpoints.
    public static let mobileKey = "mob-f2aca03d-4a84-4b9d-bc35-db20cbb4ca0a"

    /// Context key used for flag evaluation in this sample.
    public static let contextKey = "skip-howdy-user"

    /// Boolean flag key shown on the Welcome tab. `feature3` is a boolean flag that
    /// exists in the environment behind ``mobileKey``.
    public static let featureFlagKey = "feature3"

    /// Typed flag keys shown on the Welcome tab. These need not exist — a missing flag
    /// simply evaluates to the default passed at the call site, which is what the demo
    /// shows.
    public static let intFlagKey = "int-flag"
    public static let doubleFlagKey = "double-flag"
    public static let stringFlagKey = "string-flag"
    public static let jsonFlagKey = "json-flag"

    /// OpenTelemetry `service.name` reported with every signal.
    public static let serviceName = "observability-skip-howdy"

    /// OpenTelemetry `service.version` reported with every signal.
    public static let serviceVersion = "1.0.0"
}
