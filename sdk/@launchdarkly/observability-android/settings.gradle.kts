pluginManagement {
    repositories {
        // Google's Maven repository is required for Android Gradle Plugin (AGP)
        google()
        // Standard Maven Central repository
        mavenCentral()
        // Gradle Plugin Portal for community plugins like Kotlin
        gradlePluginPortal()
    }

    // Define plugin versions used across the project
    plugins {
        // Android Gradle Plugin
        id("com.android.library") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "2.2.0"
        id("com.getkeepsafe.dexcount") version "4.0.0"
    }
}

rootProject.name = "observability-android"
include("lib")

include(":launchdarkly-android-client-sdk")
project(":launchdarkly-android-client-sdk").projectDir =
    file("/Users/abelonogov/work/android-client-sdk/launchdarkly-android-client-sdk")

include(":shared-test-code")
project(":shared-test-code").projectDir =
    file("/Users/abelonogov/work/android-client-sdk/shared-test-code")
