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
# retrace obfuscated Java/Kotlin frames back to original source lines (Symbols Id Lane).
-keepattributes SourceFile,LineNumberTable

# Replace the original source file name with "SourceFile"; the backend derives
# the real file name from the retraced class, so this is safe and hides source
# names in shipped stack traces.
-renamesourcefileattribute SourceFile

# The observability SDK and OpenTelemetry rely on reflection / service loading;
# keep them intact so the app runs. The demo app's own classes
# (com.example.androidobservability.*) are still obfuscated, which is what the
# Symbols Id Lane retrace demo exercises.
-keep class com.launchdarkly.** { *; }
-dontwarn com.launchdarkly.**
-keep class io.opentelemetry.** { *; }
-dontwarn io.opentelemetry.**

# snakeyaml (pulled in transitively) references desktop java.beans APIs that do
# not exist on Android. They are only used on the JVM, so it is safe to ignore.
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**
