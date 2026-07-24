# Consumer ProGuard/R8 rules shipped with the LaunchDarkly Flutter observability
# plugin. These are applied automatically to any app that minifies a release
# build (e.g. `flutter build apk --obfuscate`).
#
# The native observability stack (launchdarkly-observability-android) bundles the
# OpenTelemetry Java SDK and Gson, which reference compile-only annotations and an
# internal logger that are intentionally absent from the runtime classpath. R8
# (AGP 8+) treats these dangling references as errors ("Missing classes detected
# while running R8") and fails the build. They are safe to ignore.

# Google ErrorProne annotations (@CanIgnoreReturnValue, @MustBeClosed, ...) are
# compile-time only and never present at runtime (referenced by Gson + OTel).
-dontwarn com.google.errorprone.annotations.**

# OpenTelemetry incubator APIs reference this internal logger reflectively; it is
# not on the runtime classpath for the API-only artifact.
-dontwarn io.opentelemetry.api.internal.ApiUsageLogger
