plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.launchdarky.LDNative"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
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
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

// Create configuration for copyDependencies
configurations {
    create("copyDependencies")
}

dependencies {

    implementation("androidx.core:core-ktx:1.17.0")
    "copyDependencies"("androidx.core:core-ktx:1.17.0")

    // Copy dependencies for binding library
    // Uncomment line below and replace dependency.name.goes.here with your dependency


    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.10.0")
    "copyDependencies"("com.launchdarkly:launchdarkly-android-client-sdk:5.10.0")

    implementation("com.launchdarkly:launchdarkly-observability-android:0.24.0")
    "copyDependencies"("com.launchdarkly:launchdarkly-observability-android:0.24.0")

    // TODO: revise these versions to be as old as usable for compatibility
    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    "copyDependencies"("io.opentelemetry:opentelemetry-api:1.51.0")

    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    "copyDependencies"("io.opentelemetry:opentelemetry-sdk:1.51.0")

    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")
    "copyDependencies"("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")

    implementation("io.opentelemetry:opentelemetry-exporter-logging-otlp:1.51.0")
    "copyDependencies"("io.opentelemetry:opentelemetry-exporter-logging-otlp:1.51.0")

    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")
    "copyDependencies"("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")

    implementation("io.opentelemetry:opentelemetry-sdk-logs:1.51.0")
    "copyDependencies"("io.opentelemetry:opentelemetry-sdk-logs:1.51.0")


    // TODO: Evaluate risks associated with incubator APIs
    implementation("io.opentelemetry:opentelemetry-api-incubator:1.51.0-alpha")
    "copyDependencies"("io.opentelemetry:opentelemetry-api-incubator:1.51.0-alpha")

    // Testing exporters for telemetry inspection
    implementation("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
    "copyDependencies"("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")


    // OTEL Android
    implementation("io.opentelemetry.android:core:0.11.0-alpha")
    "copyDependencies"("io.opentelemetry.android:core:0.11.0-alpha")

    implementation("io.opentelemetry.android:session:0.11.0-alpha")
    "copyDependencies"("io.opentelemetry.android:session:0.11.0-alpha")


    // OTEL Android Instrumentations
    implementation("io.opentelemetry.android.instrumentation:crash:0.11.0-alpha")
    "copyDependencies"("io.opentelemetry.android.instrumentation:crash:0.11.0-alpha")

    implementation("io.opentelemetry.android.instrumentation:activity:0.11.0-alpha")
    "copyDependencies"("io.opentelemetry.android.instrumentation:activity:0.11.0-alpha")


    implementation("io.opentelemetry.android:android-agent:0.11.0-alpha")
    "copyDependencies"("io.opentelemetry.android:android-agent:0.11.0-alpha")

//    implementation("io.opentelemetry.android:opentelemetry-android-bom:0.11.0-alpha")
//    "copyDependencies"("io.opentelemetry.android:opentelemetry-android-bom:0.11.0-alpha")
}

// Copy dependencies for binding library
project.afterEvaluate {
    tasks.register<Copy>("copyDeps") {
        from(configurations["copyDependencies"])
        into("${buildDir}/outputs/deps")
    }
    tasks.named("preBuild") { finalizedBy("copyDeps") }
}
