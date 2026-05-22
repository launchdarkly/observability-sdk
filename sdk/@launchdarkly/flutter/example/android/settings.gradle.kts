pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    // Align with observability-android (AGP 8.13.2) — the composite-build
    // substitution below pulls in that AGP version, and Gradle disallows
    // mixing AGP versions within a single build.
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

include(":app")

// Build the in-monorepo `observability-android` library and substitute it for
// the published Maven artifact that `launchdarkly_flutter_session_replay`'s
// `android/build.gradle` declares. The substitution applies to every project
// in this composite build (including Flutter plugin projects loaded by
// `dev.flutter.flutter-plugin-loader`), so both the plugin and the example app
// resolve the same in-tree AAR.
//
// This mirrors the .NET MAUI sample's setup at
// `sdk/@launchdarkly/mobile-dotnet/android/native/settings.gradle.kts`, and
// matches the Dart-side behavior where the example uses `path:` deps for the
// `observability` and `session_replay` packages.
includeBuild("../../../observability-android") {
    dependencySubstitution {
        substitute(module("com.launchdarkly:launchdarkly-observability-android"))
            .using(project(":lib"))
    }
}
