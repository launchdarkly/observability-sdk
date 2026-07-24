import com.android.build.api.artifact.SingleArtifact
import java.security.MessageDigest
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.androidobservability"
        minSdk = 23
        versionCode = 1
        versionName = "1.0.1"

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
            // Enabled so R8 obfuscates the app and emits mapping.txt, which the
            // build stamps into the app (Symbols Id Lane symbols id) and stages for upload.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    flavorDimensions += "uiFramework"
    productFlavors {
        create("compose") {
            dimension = "uiFramework"
        }
        create("java") {
            dimension = "uiFramework"
            applicationIdSuffix = ".java"
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

tasks.withType<Test>().configureEach {
    val loggingConfig = project.file("src/testCompose/resources/logging.properties")
    if (loggingConfig.exists()) {
        systemProperty("java.util.logging.config.file", loggingConfig.absolutePath)
    }
}

dependencies {
    // Uncomment to use the local project
    implementation(project(":observability-android"))
    // Uncomment to use the publicly released version (note this may be behind branch/main)
    // implementation("com.launchdarkly:launchdarkly-observability-android:0.2.0")

    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.11.0")

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
    // isComposeAvailable runtime check still returns false in the java variant.
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.runtime:runtime")

    // Compose UI dependencies -- only for the compose flavor
    "composeImplementation"(libs.androidx.activity.compose)
    "composeImplementation"(libs.androidx.ui)
    "composeImplementation"(libs.androidx.ui.graphics)
    "composeImplementation"(libs.androidx.ui.tooling.preview)
    "composeImplementation"(libs.androidx.material3)

    // The pure-Java flavor uses AppCompatActivity for proper Material Components theme resolution.
    "javaImplementation"("androidx.appcompat:appcompat:1.7.0")
    // Provides AndroidViewModel/ViewModelProvider used by the Java MainActivity and ViewModel.
    "javaImplementation"("androidx.lifecycle:lifecycle-viewmodel:2.6.1")

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

// --- LaunchDarkly Android symbolication (Symbols Id Lane) ---
//
// For each obfuscated (release) variant, derive a deterministic symbols id from
// R8's mapping.txt and:
//   1. embed it as assets/ld_symbols_id.txt, so the SDK reports it as the
//      resource attribute `launchdarkly.symbols_id.htlhash`; and
//   2. stage mapping.txt + mapping.txt.symbolsid under build/symbols/<variant>/ so
//      `ldcli symbols upload --type android` keys the upload by the same id.
//
// The symbols id is htlhash(mapping.txt) — a content hash of the mapping, NOT of
// the app — so injecting it back into the app's assets never changes the mapping
// (no self-reference). The task consumes the R8 mapping and transforms the merged
// ASSETS artifact, so Gradle orders it after R8 and before packaging.
abstract class StampSymbolsIdTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputAssets: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputAssets: DirectoryProperty

    @get:OutputDirectory
    abstract val symbolsDir: DirectoryProperty

    @TaskAction
    fun run() {
        val outDir = outputAssets.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        val inDir = inputAssets.get().asFile
        if (inDir.exists()) {
            inDir.copyRecursively(outDir, overwrite = true)
        }

        val mapping = mappingFile.get().asFile
        val symbolsId = htlhash(mapping.readBytes())

        // 1. Embed the symbols id for the SDK to report at runtime.
        outDir.resolve("ld_symbols_id.txt").writeText(symbolsId)

        // 2. Stage mapping + sidecar for `ldcli symbols upload --type android`.
        val sym = symbolsDir.get().asFile
        sym.deleteRecursively()
        sym.mkdirs()
        mapping.copyTo(sym.resolve("mapping.txt"), overwrite = true)
        sym.resolve("mapping.txt.symbolsid").writeText(symbolsId)

        logger.lifecycle(
            "LaunchDarkly: symbols id $symbolsId (mapping ${mapping.length()} bytes) -> " +
                "assets/ld_symbols_id.txt + ${sym.resolve("mapping.txt.symbolsid")}"
        )
    }

    // OTel htlhash: sha256 over head(4096) + tail(4096) + 8-byte LE length,
    // truncated to 16 bytes (32 hex chars). Matches the React Native Metro
    // plugin so the id shape is identical across platforms.
    private fun htlhash(buffer: ByteArray): String {
        val headTail = 4096
        val md = MessageDigest.getInstance("SHA-256")
        if (buffer.size <= headTail * 2) {
            md.update(buffer)
        } else {
            md.update(buffer, 0, headTail)
            md.update(buffer, buffer.size - headTail, headTail)
        }
        val lenBuf = ByteArray(8)
        var len = buffer.size.toLong()
        for (i in 0 until 8) {
            lenBuf[i] = (len and 0xFF).toByte()
            len = len ushr 8
        }
        md.update(lenBuf)
        return md.digest().joinToString("") { "%02x".format(it) }.substring(0, 32)
    }
}

androidComponents {
    onVariants { variant ->
        // Only obfuscated (release) variants produce a mapping.txt to key by.
        if (variant.buildType != "release") return@onVariants

        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        val stamp = tasks.register<StampSymbolsIdTask>("stampLaunchDarklySymbolsId$capitalized") {
            mappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
            symbolsDir.set(layout.buildDirectory.dir("symbols/${variant.name}"))
        }

        variant.artifacts
            .use(stamp)
            .wiredWithDirectories(StampSymbolsIdTask::inputAssets, StampSymbolsIdTask::outputAssets)
            .toTransform(SingleArtifact.ASSETS)
    }
}
