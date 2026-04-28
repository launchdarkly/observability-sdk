import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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

    implementation("io.opentelemetry:opentelemetry-exporter-sender-okhttp:1.51.0")
    "copyDependencies"("io.opentelemetry:opentelemetry-exporter-sender-okhttp:1.51.0")

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

// Custom task: merges all transitive JARs (OTel + okhttp + okio + ...) into one
// fat JAR with META-INF/services concatenated per service name.
//
// Why: .NET-for-Android 10 ships a new System.IO.Compression-based BuildArchive
// task (dotnet/android#9623) that, when collecting Java resources from
// `<AndroidJavaLibrary>` items, silently skips any JAR entry whose `ArchivePath`
// already exists in the APK. With ~15 OTel JARs shipped individually, several
// `META-INF/services/*` paths overlap (e.g. ComponentProvider exists in
// opentelemetry-exporter-otlp, -exporter-logging, -exporter-logging-otlp), and
// the unique HttpSenderProvider in opentelemetry-exporter-sender-okhttp gets
// dropped along with them, breaking ServiceLoader at runtime with
// "No HttpSenderProvider found on classpath".
//
// By collapsing every JAR into one, no two FilesToAddToArchive items share an
// ArchivePath, and the merged services file is the single source of truth for
// every SPI we ship. .NET 9 consumers behave identically (one JAR vs many).
abstract class BundleOtelJarsTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputFiles
    abstract val inputJars: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputJar: org.gradle.api.file.RegularFileProperty

    // Directory that mirrors META-INF/services/* from the bundle JAR as plain
    // files on disk. The .NET-for-Android packaging task hardcodes "META-INF"
    // as a stripped folder when collecting JAR resources for the APK
    // (PackagingUtils.CheckEntryForPackaging), so the merged service files
    // inside the JAR never reach the final APK and ServiceLoader fails at
    // runtime. By emitting them as standalone files we can re-add them to
    // @(FilesToAddToArchive) in MSBuild *after* the strip-aware
    // CollectJarContentFilesForArchive task runs, so BuildArchive (which does
    // not filter on path) places them at META-INF/services/* in the APK.
    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputServicesDir: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun bundle() {
        val out = outputJar.get().asFile
        out.parentFile.mkdirs()

        val servicesDir = outputServicesDir.get().asFile
        if (servicesDir.exists()) servicesDir.deleteRecursively()
        servicesDir.mkdirs()

        val classOrResourceEntries = linkedMapOf<String, ByteArray>()
        val serviceFiles = linkedMapOf<String, MutableList<String>>()

        inputJars.files.sortedBy { it.name }.forEach { jar ->
            ZipFile(jar).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name == "META-INF/MANIFEST.MF") continue
                    if (name.startsWith("META-INF/") &&
                        (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA"))) continue

                    if (name.startsWith("META-INF/services/")) {
                        zip.getInputStream(entry).use { input ->
                            val content = input.bufferedReader().readText()
                            serviceFiles.getOrPut(name) { mutableListOf() }.add(content)
                        }
                    } else if (!classOrResourceEntries.containsKey(name)) {
                        zip.getInputStream(entry).use { input ->
                            classOrResourceEntries[name] = input.readBytes()
                        }
                    }
                }
            }
        }

        ZipOutputStream(out.outputStream().buffered()).use { zout ->
            zout.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zout.write("Manifest-Version: 1.0\nCreated-By: ldobserve-otel-bundle\n".toByteArray())
            zout.closeEntry()

            serviceFiles.forEach { (path, contents) ->
                val merged = contents
                    .flatMap { it.lineSequence() }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .distinct()
                    .joinToString("\n") + "\n"
                zout.putNextEntry(ZipEntry(path))
                zout.write(merged.toByteArray())
                zout.closeEntry()

                // Also write the merged service file as a standalone file so
                // MSBuild can pick it up and inject it into the APK directly.
                // Strip the "META-INF/services/" prefix; the consumer-side
                // target re-applies it as ArchivePath.
                val serviceName = path.removePrefix("META-INF/services/")
                File(servicesDir, serviceName).writeText(merged)
            }

            classOrResourceEntries.forEach { (name, data) ->
                zout.putNextEntry(ZipEntry(name))
                zout.write(data)
                zout.closeEntry()
            }
        }

        logger.lifecycle(
            "ldobserve-otel-bundle: ${classOrResourceEntries.size} class/resource entries, " +
                "${serviceFiles.size} merged service files, ${inputJars.files.size} input JARs"
        )
    }
}

project.afterEvaluate {
    val observabilityBuild = gradle.includedBuild("observability-android")
    val observabilityAarDir = File(observabilityBuild.projectDir, "lib/build/outputs/aar")
    val depsDir = layout.buildDirectory.dir("outputs/deps").get().asFile
    val bundleStaging = layout.buildDirectory.dir("tmp/ldobserve-otel-bundle").get().asFile

    // Mirror the previous NativeAndroidDeps.props allowlist:
    //   include: opentelemetry-*.jar, okhttp-*.jar, okio-*.jar
    //   exclude: opentelemetry-sdk-extension-incubator-*.jar,
    //            opentelemetry-sdk-extension-autoconfigure-*.jar  (main + SPI)
    //            opentelemetry-sdk-testing-*.jar
    // Other JARs in `copyDependencies` (jackson, snakeyaml, gson, launchdarkly,
    // wire, kotlin-stdlib, AndroidX, etc.) are transitives we did not ship
    // historically and bundling them would inflate the NuGet ~5 MB.
    val bundleExcludePrefixes = listOf(
        "opentelemetry-sdk-extension-incubator-",
        "opentelemetry-sdk-extension-autoconfigure-",
        "opentelemetry-sdk-testing-",
    )
    val servicesStaging = layout.buildDirectory.dir("tmp/ldobserve-otel-services").get().asFile

    val bundleOtelJars = tasks.register<BundleOtelJarsTask>("bundleOtelJars") {
        inputJars.from(configurations["copyDependencies"].filter { f ->
            val n = f.name
            if (!n.endsWith(".jar")) return@filter false
            val included = n.startsWith("opentelemetry-") ||
                n.startsWith("okhttp-") ||
                n.startsWith("okio-")
            included && bundleExcludePrefixes.none { n.startsWith(it) }
        })
        outputJar.set(File(bundleStaging, "ldobserve-otel-bundle.jar"))
        outputServicesDir.set(servicesStaging)
    }

    // Sync AARs from copyDependencies (and the locally-built observability AAR),
    // the merged OTel bundle JAR, and the standalone META-INF/services files
    // into outputs/deps. Sync (not Copy) ensures stale individual JARs from
    // older builds are removed; only the single ldobserve-otel-bundle.jar
    // remains alongside the AARs and the META-INF/services/ tree.
    tasks.register<Sync>("copyDeps") {
        dependsOn(observabilityBuild.task(":lib:assemble"))
        dependsOn(bundleOtelJars)
        from(configurations["copyDependencies"].filter { it.name.endsWith(".aar") })
        from(fileTree(observabilityAarDir) { include("*.aar") })
        from(bundleStaging)
        // META-INF/services/* — laid out under META-INF/services/ in the
        // output so MSBuild can glob them by stable path.
        from(servicesStaging) {
            into("META-INF/services")
        }
        into(depsDir)
    }
    tasks.named("preBuild") { finalizedBy("copyDeps") }
}
