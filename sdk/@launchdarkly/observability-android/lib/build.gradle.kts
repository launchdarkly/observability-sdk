import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.FileInputStream
import java.io.FileOutputStream


plugins {
    // Apply the Android library plugin
    id("com.android.library")
    id("maven-publish")
    id("signing")

    // Added with Otel Http URL instrumentation
    id("net.bytebuddy.byte-buddy-gradle-plugin") version "1.17.6"

    // Apply the Kotlin Android plugin for Android-compatible Kotlin support.
    alias(libs.plugins.kotlin.android)
}

allprojects {
    repositories {
        google() // Google's Maven repository
        mavenCentral() // Maven Central repository
        mavenLocal()
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.9.3")

    // TODO: revise these versions to be as old as usable for compatibility
    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-logging-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-logs:1.51.0")

    // TODO: Evaluate risks associated with incubator APIs
    implementation("io.opentelemetry:opentelemetry-api-incubator:1.51.0-alpha")

    // Android instrumentation core
    implementation("io.opentelemetry.android:core:0.11.0-alpha")

    // Session id assignment
    implementation("io.opentelemetry.android:session:0.11.0-alpha")

    // Activity lifecycle instrumentation
    implementation("io.opentelemetry.android.instrumentation:activity:0.11.0-alpha")

    // Http URL instrumentation
    implementation("io.opentelemetry.android.instrumentation:httpurlconnection-library:0.11.0-alpha")
    byteBuddy("io.opentelemetry.android.instrumentation:httpurlconnection-agent:0.11.0-alpha")

    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val releaseVersion = version.toString()

android {
    namespace = "com.launchdarkly.observability"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24
        version = releaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OBSERVABILITY_SDK_VERSION", "\"${project.version}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

abstract class ClassScannerTask : DefaultTask() {
    @TaskAction
    fun scanClasses() {
        // Use Android-specific configurations for library
        val androidConfigurations = project.extensions.getByType(com.android.build.gradle.LibraryExtension::class.java)

        println("=== LaunchDarkly Classes in Classpath ===")

        // Get all variant configurations for library
        androidConfigurations.libraryVariants.all { variant ->
            var totalClassesFound = 0
            // Use compile configuration for the variant
            val compileConfigurationName = "${variant.name}CompileClasspath"
            val compileConfiguration = project.configurations.getByName(compileConfigurationName)

            // Use runtime configuration for the variant
            val runtimeConfigurationName = "${variant.name}RuntimeClasspath"
            val runtimeConfiguration = project.configurations.getByName(runtimeConfigurationName)

            println("\nVariant: ${variant.name}")
            println("-------------------")

            println("\nCompile Classpath:")
            println("==================")
            // Scan all files in the compile configuration
            for (file in compileConfiguration.files) {
                println("Scanning: ${file.name}")
                totalClassesFound += scanFile(file)
            }

            println("\nRuntime Classpath:")
            println("==================")
            // Scan all files in the runtime configuration
            for (file in runtimeConfiguration.files) {
                println("Scanning: ${file.name}")
                totalClassesFound += scanFile(file)
            }
            
            println("\nTotal LaunchDarkly classes found: $totalClassesFound")
            true // Return true to continue processing
        }
    }

    private fun scanFile(file: File): Int {
        var classesFound = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach { classesFound += scanFile(it) }
        } else if (file.name.endsWith(".jar") || file.name.endsWith(".aar")) {
            try {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (entry.name.endsWith(".class") && entry.name.contains("launchdarkly")) {
                            val className = entry.name.replace('/', '.').removeSuffix(".class")
                            println("  $className")
                            classesFound++
                        } else if (entry.name == "classes.jar" && file.name.endsWith(".aar")) {
                            // Extract and scan nested classes.jar from AAR files
                            val tempDir = File.createTempFile("aar_extract", null).parentFile!!
                            try {
                                val tempJar = File(tempDir, "classes.jar")
                                zip.getInputStream(entry).use { input ->
                                    tempJar.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                classesFound += scanFile(tempJar)
                            } finally {
                                tempDir.deleteRecursively()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error scanning ${file.absolutePath}: ${e.message}")
            }
        }
        return classesFound
    }
}

// Register and configure the task
tasks.register<ClassScannerTask>("printAvailableClasses") {
    group = "Development"
    description = "Prints all available classes in the compile classpath"

    // Make it dependent on compileDebugKotlin to ensure classpath is ready
    dependsOn(tasks.named("compileDebugKotlin"))
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.launchdarkly"
            artifactId = "launchdarkly-observability-android"
            version = releaseVersion

            pom {
                name.set("LaunchDarkly Observability Android SDK")
                description.set(
                    "Official LaunchDarkly Observability Android SDK for use with the LaunchDarkly Android SDK."
                )
                url.set("https://github.com/launchdarkly/observability-sdk/")
                organization {
                    name.set("LaunchDarkly")
                    url.set("https://launchdarkly.com/")
                }
                developers {
                    developer {
                        id.set("sdks")
                        name.set("LaunchDarkly SDK Team")
                        email.set("sdks@launchdarkly.com")
                    }
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set(
                        "scm:git:https://github.com/launchdarkly/observability-sdk.git"
                    )
                    developerConnection.set(
                        "scm:git:ssh:github.com/launchdarkly/observability-sdk.git"
                    )
                    url.set("https://github.com/launchdarkly/observability-sdk/")
                }
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

signing {
    sign(publishing.publications["release"])
}
