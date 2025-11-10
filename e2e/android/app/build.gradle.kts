plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Uncomment to use the local project
    implementation(project(":observability-android"))
    // Uncomment to use the publicly released version (note this may be behind branch/main)
    // implementation("com.launchdarkly:launchdarkly-observability-android:0.2.0")

    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.9.0")

    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")

    // Android HTTP Url instrumentation
    implementation("io.opentelemetry.android.instrumentation:httpurlconnection-library:0.11.0-alpha")
    byteBuddy("io.opentelemetry.android.instrumentation:httpurlconnection-agent:0.11.0-alpha")

    // Used for accessing the SignalFromDiskExporter class in TestApplication
    implementation("io.opentelemetry.android:core:0.11.0-alpha")

    // OkHTTP instrumentation
    implementation("io.opentelemetry.android.instrumentation:okhttp3-library:0.11.0-alpha")
    byteBuddy("io.opentelemetry.android.instrumentation:okhttp3-agent:0.11.0-alpha")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

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
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
    testImplementation(testFixtures(project(":observability-android")))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
