plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.launchdarkly.LDNative"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
}

// Create configuration for copyDependencies
configurations {
    create("copyDependencies")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    "copyDependencies"("androidx.core:core-ktx:1.15.0")

    // Copy dependencies for binding library
    // Uncomment line below and replace dependency.name.goes.here with your dependency
    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.11.0")
    "copyDependencies"("com.launchdarkly:launchdarkly-android-client-sdk:5.11.0")

    // Intentionally use a non-existent version so this dependency MUST be
    // satisfied by composite-build substitution (settings.gradle.kts).
    // If substitution breaks, the build should fail instead of silently
    // falling back to an older published AAR.
    implementation("com.launchdarkly:launchdarkly-observability-android:0.0.0-local")

    // TODO: revise these versions to be as old as usable for compatibility
    // OpenTelemetry JARs copied here are filtered for NuGet in observability/LDObservability.Fat.csproj (autoconfigure vs autoconfigure-spi).
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
}

// Copy dependencies for binding library
project.afterEvaluate {
    val observabilityBuild = gradle.includedBuild("observability-android")
    val observabilityAarDir = File(observabilityBuild.projectDir, "lib/build/outputs/aar")

    tasks.register<Copy>("copyDeps") {
        from(configurations["copyDependencies"])
        dependsOn(observabilityBuild.task(":lib:assemble"))
        from(fileTree(observabilityAarDir) { include("*.aar") })
        into("${buildDir}/outputs/deps")
    }
    tasks.named("preBuild") { finalizedBy("copyDeps") }
}
