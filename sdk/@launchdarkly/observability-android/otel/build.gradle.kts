plugins {
    id("com.android.library")
    id("maven-publish")
    id("signing")

    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)

    id("org.jetbrains.dokka") version "2.1.0"
    id("org.jetbrains.dokka-javadoc") version "2.1.0"
}

repositories {
    google()
    mavenCentral()
}

// Mirrors the opt-in in :lib. Hosts that already provide the LaunchDarkly Android client SDK on the
// app classpath (the MAUI bridge, and Flutter via launchdarkly_flutter_client_sdk) set
// `ldClientSdkProvided=true`, which must be honoured identically here because this module carries
// the plugin and hook integration.
val isClientSdkProvidedByHost =
    (providers.gradleProperty("ldClientSdkProvided").orNull
        ?: providers.systemProperty("ldClientSdkProvided").orNull)
        ?.toBoolean() == true

// See the equivalent block in lib/build.gradle.kts for why the Kotlin runtime artifacts are pinned.
configurations.all {
    resolutionStrategy.eachDependency {
        val name = requested.name
        val isRuntimeArtifact = name.startsWith("kotlin-stdlib") ||
                name == "kotlin-reflect" ||
                name.startsWith("kotlin-test")
        if (requested.group == "org.jetbrains.kotlin" && isRuntimeArtifact) {
            useVersion("2.0.21")
            because("Align Kotlin runtime artifacts with the project's Kotlin compiler version (2.0.21).")
        }
    }
}

dependencies {
    if (isClientSdkProvidedByHost) {
        compileOnly("com.launchdarkly:launchdarkly-android-client-sdk:5.13.1")
        testImplementation("com.launchdarkly:launchdarkly-android-client-sdk:5.13.1")
    } else {
        implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.13.1")
    }

    // Process foreground/background transitions, which drive session background-timeout rotation.
    // This is the only AndroidX dependency the OTel-only product needs.
    implementation("androidx.lifecycle:lifecycle-process:2.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // The OpenTelemetry SDK proper. Note the deliberate absence of `io.opentelemetry.android:*`:
    // this module builds a plain OpenTelemetrySdk rather than an OpenTelemetryRum, so it never
    // discovers or installs AndroidInstrumentation implementations from the host's classpath.
    api("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-logs:1.51.0")

    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.mockk:mockk:1.14.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // AppLifecycleTrackerTest's fake owner overrides `lifecycle` as a Kotlin property, which
    // androidx.lifecycle only exposes from 2.6.0. Production code uses the getter, so the
    // published floor above stays at 2.4.0 and only the tests resolve higher.
    testImplementation("androidx.lifecycle:lifecycle-common:2.6.1")
}

// Applied to every Kotlin compilation (main, unit test, and test fixtures) rather than through
// `android { kotlin { ... } }`, which does not reach the test-fixtures variant.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.optIn.add("com.launchdarkly.observability.InternalObservabilityApi")
}

val releaseVersion = version.toString()

tasks.withType<Test> {
    useJUnitPlatform()
}

android {
    // Distinct from :lib's `com.launchdarkly.observability` because AGP requires a unique namespace
    // per module and generates BuildConfig into it. The Kotlin source packages are unchanged, so
    // existing imports keep resolving.
    namespace = "com.launchdarkly.otel"
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
        coreLibrariesVersion = "2.0.21"
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
            apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        }
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.launchdarkly"
            artifactId = "launchdarkly-otel-android"
            version = releaseVersion

            pom {
                name.set("LaunchDarkly OpenTelemetry Android SDK")
                description.set(
                    "OpenTelemetry-only LaunchDarkly Android SDK: manual recording APIs and OTLP " +
                        "export with no automatic instrumentation."
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
    moduleName.set("launchdarkly-otel-android")
    moduleVersion.set(project.version.toString())

    dokkaPublications.javadoc {
        outputDirectory.set(layout.projectDirectory.dir("docs"))
    }

    dokkaSourceSets.configureEach {
        includes.from("doc-module.md")
    }
}
