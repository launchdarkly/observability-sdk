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

fun String?.isTruthy(): Boolean =
    this.equals("true", ignoreCase = true) ||
        this == "1" ||
        this.equals("yes", ignoreCase = true)

val useLocalNativeSdk = providers.gradleProperty("ldUseLocalNative")
    .orElse(providers.environmentVariable("LD_USE_LOCAL_NATIVE"))
    .orNull
    .isTruthy()

if (useLocalNativeSdk) {
    val observabilityAndroidPath = providers.gradleProperty("ldObservabilityAndroidPath")
        .orElse(providers.environmentVariable("LD_OBSERVABILITY_ANDROID_PATH"))
        .orElse("../../../observability-android")
        .get()

    // Build the local `observability-android` library and substitute it for
    // the published Maven artifact declared by the Flutter session replay
    // plugin. This applies to all projects in the Flutter composite build.
    includeBuild(observabilityAndroidPath) {
        dependencySubstitution {
            substitute(module("com.launchdarkly:launchdarkly-observability-android"))
                .using(project(":lib"))
        }
    }
}
