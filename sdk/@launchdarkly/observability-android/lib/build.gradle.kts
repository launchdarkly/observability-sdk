plugins {
    // Apply the Android library plugin
    id("com.android.library")
    id("maven-publish")
    id("signing")

    // Apply the Kotlin Android plugin for Android-compatible Kotlin support.
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)

    // Apply Dokka plugin for documentation generation
    id("org.jetbrains.dokka") version "2.1.0"
    id("org.jetbrains.dokka-javadoc") version "2.1.0"
}

allprojects {
    repositories {
        google() // Google's Maven repository
        mavenCentral() // Maven Central repository
    }
}

val isIncludedByMaui = gradle.parent?.rootProject?.name == "LDObserve"

// Pin all Kotlin artifacts (stdlib, reflect, etc.) on this build's classpath to the same
// version as the configured Kotlin compiler. Without this, transitive deps such as the
// io.opentelemetry.android:*:0.11.0-alpha modules drag in newer Kotlin artifacts whose
// metadata version this compiler cannot read, producing
// "Module was compiled with an incompatible version of Kotlin" errors at compileDebugKotlin.
// All org.jetbrains.kotlin:* artifacts ship in lock-step with the compiler, so aligning the
// whole group is the right scope here (covers kotlin-stdlib*, kotlin-reflect, etc.).
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
            because("Align all org.jetbrains.kotlin:* artifacts with the project's Kotlin compiler version (2.0.21).")
        }
    }
}

dependencies {
    if (isIncludedByMaui) {
        compileOnly("com.launchdarkly:launchdarkly-android-client-sdk:5.12.0")
        testImplementation("com.launchdarkly:launchdarkly-android-client-sdk:5.12.0")
    } else {
        implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.12.0")
    }

    // AndroidX
    // This only used by Session Replay.
    implementation("androidx.activity:activity:1.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.4.0")
    compileOnly("androidx.compose.ui:ui:1.7.5")
    compileOnly("androidx.compose.ui:ui-tooling:1.7.5")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Kotlinx serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // TODO: revise these versions to be as old as usable for compatibility
    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-logging-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-logs:1.51.0")

    // Required at runtime by io.opentelemetry.android:core, which uses incubator APIs
    // internally for the logs bridge. Can be removed once the OTel Android SDK drops this dependency.
    implementation("io.opentelemetry:opentelemetry-api-incubator:1.51.0-alpha")

    // OTEL Android
    implementation("io.opentelemetry.android:core:0.11.0-alpha")
    implementation("io.opentelemetry.android:session:0.11.0-alpha")

    // OTEL Android Instrumentations
    implementation("io.opentelemetry.android.instrumentation:crash:0.11.0-alpha")
    implementation("io.opentelemetry.android.instrumentation:activity:0.11.0-alpha")

    // Use JUnit Jupiter for testing.
    // Testing exporters for telemetry inspection
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.mockk:mockk:1.14.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    testFixturesApi("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

val releaseVersion = version.toString()

tasks.withType<Test> {
    useJUnitPlatform()
}

android {
    namespace = "com.launchdarkly.observability"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 23
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }

    testFixtures {
        enable = true
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.launchdarkly"
            artifactId = "launchdarkly-observability-android"
            version = releaseVersion

            // artifact(dokkaJar)

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
    isRequired = gradle.taskGraph.allTasks.any { it.name.contains("sonatype", ignoreCase = true) }
    sign(publishing.publications["release"])
}

dokka {
    moduleName.set("launchdarkly-observability-android")
    moduleVersion.set(project.version.toString())

    dokkaPublications.javadoc {
        outputDirectory.set(layout.projectDirectory.dir("docs"))
    }

    dokkaSourceSets.configureEach {
        includes.from("doc-module.md")
    }
}
