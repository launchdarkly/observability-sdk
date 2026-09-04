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
        // Keep this aligned with the locally included android-client-sdk build. Gradle does not
        // allow different Android Gradle Plugin versions within one composite build.
        id("com.android.library") version "8.3.2"
    }
}

rootProject.name = "observability-android"
include("lib")

includeBuild("../../../../android-client-sdk") {
    dependencySubstitution {
        substitute(module("com.launchdarkly:launchdarkly-android-client-sdk"))
            .using(project(":launchdarkly-android-client-sdk"))
    }
}
