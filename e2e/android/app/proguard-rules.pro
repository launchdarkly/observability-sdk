# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers so R8 can emit a precise mapping.txt and the backend can
# retrace obfuscated Java/Kotlin frames back to original source lines.
-keepattributes SourceFile,LineNumberTable

# Note there is deliberately no -renamesourcefileattribute here. R8 uses that
# attribute to stamp every class with "r8-map-id-<hash of the mapping>", which is
# how a crash says which mapping retraces it (Symbols Id Lane), and it hides the
# real source file names just the same. Add the rule back to see the app fall
# through to the Version Lane instead — see SYMBOLICATION.md.

# The observability SDK and OpenTelemetry rely on reflection / service loading;
# keep them intact so the app runs. The demo app's own classes
# (com.example.androidobservability.*) are still obfuscated, which is what the
# retrace demo exercises.
-keep class com.launchdarkly.** { *; }
-dontwarn com.launchdarkly.**
-keep class io.opentelemetry.** { *; }
-dontwarn io.opentelemetry.**

# snakeyaml (pulled in transitively) references desktop java.beans APIs that do
# not exist on Android. They are only used on the JVM, so it is safe to ignore.
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**
