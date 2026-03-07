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
        id("com.android.test") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "2.2.0"
        id("androidx.baselineprofile") version "1.3.4"
    }
}

rootProject.name = "observability-android"
include("lib")
