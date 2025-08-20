plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Added with Otel Http URL instrumentation
    id("net.bytebuddy.byte-buddy-gradle-plugin") version "1.17.6"
}

android {
    namespace = "com.example.androidobservability"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.androidobservability"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Uncomment to use the local project
    implementation(project(":observability-android"))
    // Uncomment to use the publicly released version (note this may be behind branch/main)
    // implementation("com.launchdarkly:launchdarkly-observability-android:0.2.0")

    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.9.3")

    // Http URL instrumentation
    implementation("io.opentelemetry.android.instrumentation:httpurlconnection-library:0.11.0-alpha")
    byteBuddy("io.opentelemetry.android.instrumentation:httpurlconnection-agent:0.11.0-alpha")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
