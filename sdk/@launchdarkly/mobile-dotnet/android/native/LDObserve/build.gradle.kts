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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
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

// Custom task: merges all transitive JARs (OTel + okhttp + okio + ...) into a
// single fat JAR containing class files, code resources, and a deduplicated
// set of META-INF/services/* SPI registrations. The same merged services
// are also emitted as standalone files on disk so the consumer-side MSBuild
// target can re-inject them via @(FilesToAddToArchive) for .NET 10 builds.
//
// Why a single fat JAR (rather than ~15 individual ones):
// .NET-for-Android 10 ships a new System.IO.Compression-based BuildArchive
// task (dotnet/android#9623) that, when collecting Java resources from
// <AndroidJavaLibrary> items, silently skips any JAR entry whose ArchivePath
// already exists in the APK. With ~15 OTel JARs shipped individually, several
// META-INF/services/* paths overlap (e.g. ComponentProvider exists in
// opentelemetry-exporter-otlp, -exporter-logging, -exporter-logging-otlp), and
// the unique HttpSenderProvider in opentelemetry-exporter-sender-okhttp gets
// dropped along with them, breaking ServiceLoader at runtime with
// "No HttpSenderProvider found on classpath".
//
// By collapsing every JAR into one, the merged services file is the single
// source of truth for every SPI we ship: each ArchivePath is unique within
// the bundle JAR, so neither .NET 9's BuildApk nor .NET 10's BuildArchive
// drops entries. .NET 9 consumers behave identically (one JAR vs many).
//
// Why META-INF/services/* IS kept inside the bundle JAR (and ALSO emitted
// as standalone files on disk):
// .NET-for-Android 9 ships the older BuildApk task, which extracts JAR
// contents into the APK directly via its JavaLibraries parameter and does
// NOT consume @(FilesToAddToArchive). The MSBuild-side _LDInjectMetaInfServices
// target therefore is a no-op on .NET 9, and stripping services from the
// bundle JAR meant ServiceLoader could not find HttpSenderProvider at
// runtime. Putting the merged services back into the JAR restores the .NET 9
// path while staying compatible with .NET 10:
//   - .NET 9 BuildApk: extracts JAR services into META-INF/services/*.
//   - .NET 10 CollectJarContentFilesForArchive: emits the same JAR services
//     into @(FilesToAddToArchive) (auto-prefixing with `root/` for AAB so
//     bundletool's XABAB0000 module-dir check is satisfied). The standalone
//     injection contributes the same paths; BuildArchive silently dedupes
//     by ArchivePath, so the two mechanisms coexist without error.
// All OTHER META-INF/* entries (MANIFEST.MF, LICENSE, NOTICE, maven/*,
// signature files, etc.) are stripped: they are not needed at runtime and
// have historically caused noise/errors across .NET-for-Android versions.
abstract class BundleOtelJarsTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputFiles
    abstract val inputJars: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputJar: org.gradle.api.file.RegularFileProperty

    // Directory containing the merged META-INF/services/* registrations as
    // plain files on disk. These are emitted IN ADDITION to the same merged
    // entries inside the bundle JAR (see the @TaskAction body). The
    // standalone files exist to drive consumer-side MSBuild targets that
    // inject them into @(FilesToAddToArchive) with the correct ArchivePath
    // (META-INF/services/* for APK, root/META-INF/services/* for AAB) on
    // .NET 10 builds. .NET 9 ignores @(FilesToAddToArchive) entirely and
    // relies on the JAR-embedded copies instead.
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

                    // Capture META-INF/services/* contents so we can both
                    // (a) re-emit a single merged copy back into the bundle
                    // JAR (so .NET 9's BuildApk extracts SPI registrations
                    // into the APK) and (b) write standalone files for the
                    // .NET 10 MSBuild injection path. Strip every other
                    // META-INF/* entry (MANIFEST.MF, LICENSE, NOTICE,
                    // maven/*, signature files): they're not needed at
                    // runtime and have historically caused noise/errors
                    // across .NET-for-Android versions.
                    if (name.startsWith("META-INF/")) {
                        if (name.startsWith("META-INF/services/")) {
                            zip.getInputStream(entry).use { input ->
                                val content = input.bufferedReader().readText()
                                serviceFiles.getOrPut(name) { mutableListOf() }.add(content)
                            }
                        }
                        continue
                    }

                    if (!classOrResourceEntries.containsKey(name)) {
                        zip.getInputStream(entry).use { input ->
                            classOrResourceEntries[name] = input.readBytes()
                        }
                    }
                }
            }
        }

        // Merge once, write twice: the same content goes into both the
        // bundle JAR and the standalone files on disk.
        val mergedServiceFiles = serviceFiles.mapValues { (_, contents) ->
            contents
                .flatMap { it.lineSequence() }
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .distinct()
                .joinToString("\n") + "\n"
        }

        // Standalone files: consumed by the .NET 10 _LDInjectMetaInfServices
        // MSBuild target which re-adds them via @(FilesToAddToArchive) with
        // the correct ArchivePath (META-INF/services/* for APK,
        // root/META-INF/services/* for AAB).
        mergedServiceFiles.forEach { (path, merged) ->
            val serviceName = path.removePrefix("META-INF/services/")
            File(servicesDir, serviceName).writeText(merged)
        }

        ZipOutputStream(out.outputStream().buffered()).use { zout ->
            zout.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zout.write("Manifest-Version: 1.0\nCreated-By: ldobserve-otel-bundle\n".toByteArray())
            zout.closeEntry()

            classOrResourceEntries.forEach { (name, data) ->
                zout.putNextEntry(ZipEntry(name))
                zout.write(data)
                zout.closeEntry()
            }

            // Embed the same merged services inside the JAR. .NET 9's
            // BuildApk task extracts JAR contents directly into the APK
            // (it ignores @(FilesToAddToArchive)), so without these
            // entries ServiceLoader fails at runtime with
            // "No HttpSenderProvider found on classpath" on .NET 9 builds.
            // Each path is unique within the bundle JAR (see the per-path
            // dedup above), so .NET 10's BuildArchive does not lose any
            // SPI registrations either; if its CollectJarContentFilesForArchive
            // also produces an entry at the same ArchivePath as the standalone
            // injection, BuildArchive silently skips the duplicate.
            mergedServiceFiles.forEach { (path, merged) ->
                zout.putNextEntry(ZipEntry(path))
                zout.write(merged.toByteArray())
                zout.closeEntry()
            }
        }

        logger.lifecycle(
            "ldobserve-otel-bundle: ${classOrResourceEntries.size} class/resource entries, " +
                "${mergedServiceFiles.size} merged service files (in-JAR + standalone), " +
                "${inputJars.files.size} input JARs"
        )
    }
}

project.afterEvaluate {
    val observabilityBuild = gradle.includedBuild("observability-android")
    // Mirror this project's buildDir relative path onto the included build's
    // `lib` module. The .NET-for-Android NLI BuildTasks invoke gradle with
    // `-Dorg.gradle.project.buildDir=bin/<Configuration>/<TFM>`, a system
    // property that Gradle applies to *every* project in the build — both
    // LDObserve and the included observability-android build. Hardcoding
    // `lib/build/outputs/aar` would miss the relocated AAR (e.g.
    // `lib/bin/Release/net9.0-android/outputs/aar/lib-release.aar`) under
    // MSBuild/NLI, so the Sync below would silently produce zero matches and
    // MSBuild later fails MSB3030 copying the missing `lib-release.aar`.
    // Direct `./gradlew` invocations keep the default `build` value and the
    // path collapses to `lib/build/outputs/aar` as before.
    val relativeBuildDir = layout.buildDirectory.get().asFile
        .relativeTo(projectDir).invariantSeparatorsPath
    val observabilityAarDir = File(observabilityBuild.projectDir, "lib/$relativeBuildDir/outputs/aar")
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
