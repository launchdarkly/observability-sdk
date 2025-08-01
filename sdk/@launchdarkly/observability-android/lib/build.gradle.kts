plugins {
    // Apply the Android library plugin
    id("com.android.library")

    // Apply the Kotlin Android plugin for Android-compatible Kotlin support.
    alias(libs.plugins.kotlin.android)
}

allprojects {
    repositories {
        google() // Google's Maven repository
        mavenCentral() // Maven Central repository
    }
}

dependencies {
    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.9.0")

    // TODO: revise these versions to be as old as usable for compatibility
    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-logging-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-logs:1.51.0")

    // Android instrumentation
    implementation("io.opentelemetry.android:core:0.10.0-alpha")
    implementation("io.opentelemetry.android:instrumentation-activity:0.10.0-alpha")
    implementation("io.opentelemetry.android:session:0.10.0-alpha")

    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

android {
    namespace = "com.launchdarkly.observability"
    compileSdk = 30

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
