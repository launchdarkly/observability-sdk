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
        id("com.android.library") version "8.13.1"
    }
}

rootProject.name = "observability-android"
include("lib")
