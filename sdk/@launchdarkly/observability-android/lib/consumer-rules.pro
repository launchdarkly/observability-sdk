# Located by name from the OTel-only core (ObservabilityExtensionsLoader) so that a standalone
# LDObserve.init still installs this artifact's instrumentation and Session Replay. Nothing
# references it statically, so R8 would otherwise remove it.
-keep class com.launchdarkly.observability.sdk.FullObservabilityExtensions {
    <init>();
}
