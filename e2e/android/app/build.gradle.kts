import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("net.bytebuddy.byte-buddy-gradle-plugin") version "1.17.6"
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
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

        buildConfigField(
            "String",
            "LAUNCHDARKLY_MOBILE_KEY",
            "\"${localProperties.getProperty("launchdarkly.mobileKey", "")}\""
        )
        buildConfigField(
            "String",
            "OTLP_ENDPOINT",
            "\"${localProperties.getProperty("launchdarkly.otlpEndpoint", "").ifEmpty { "https://otel.observability.app.launchdarkly.com:4318" }}\""
        )
        buildConfigField(
            "String",
            "BACKEND_URL",
            "\"${localProperties.getProperty("launchdarkly.backendUrl", "").ifEmpty { "https://pub.observability.app.launchdarkly.com" }}\""
        )
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
    flavorDimensions += "uiFramework"
    productFlavors {
        create("compose") {
            dimension = "uiFramework"
        }
        create("noCompose") {
            dimension = "uiFramework"
            applicationIdSuffix = ".nocompose"
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
        buildConfig = true
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

    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.10.0")

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
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose runtime is needed by the Kotlin Compose compiler plugin (applied project-wide).
    // It does NOT contain any UI classes like AbstractComposeView, so the SDK's
    // isComposeAvailable runtime check still returns false in the noCompose variant.
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.runtime:runtime")

    // Compose UI dependencies -- only for the compose flavor
    "composeImplementation"(libs.androidx.activity.compose)
    "composeImplementation"(libs.androidx.ui)
    "composeImplementation"(libs.androidx.ui.graphics)
    "composeImplementation"(libs.androidx.ui.tooling.preview)
    "composeImplementation"(libs.androidx.material3)

    // noCompose uses AppCompatActivity for proper Material Components theme resolution
    "noComposeImplementation"("androidx.appcompat:appcompat:1.7.0")

    testImplementation(libs.junit)
    testImplementation(libs.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(testFixtures(project(":observability-android")))

    // Used for testing webviews masking
    implementation("org.mozilla.geckoview:geckoview:130.0.20240913135723")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
