import java.net.URL

plugins {
    // Apply the Android library plugin
    id("com.android.library")
    id("maven-publish")
    id("signing")

    // Apply the Kotlin Android plugin for Android-compatible Kotlin support.
    alias(libs.plugins.kotlin.android)

    // Apply Dokka plugin for documentation generation
    id("org.jetbrains.dokka") version "2.0.0"
}

allprojects {
    repositories {
        google() // Google's Maven repository
        mavenCentral() // Maven Central repository
    }
}

dependencies {
    implementation("com.launchdarkly:launchdarkly-android-client-sdk:5.9.0")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // TODO: revise these versions to be as old as usable for compatibility
    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-logging-otlp:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-logs:1.51.0")

    // TODO: Evaluate risks associated with incubator APIs
    implementation("io.opentelemetry:opentelemetry-api-incubator:1.51.0-alpha")

    // Android instrumentation
    implementation("io.opentelemetry.android:core:0.11.0-alpha")
    implementation("io.opentelemetry.android.instrumentation:activity:0.11.0-alpha")
    implementation("io.opentelemetry.android:session:0.11.0-alpha")

    // Android crash instrumentation
    implementation("io.opentelemetry.android.instrumentation:crash:0.11.0-alpha")

    // Use JUnit Jupiter for testing.
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // MockK for mocking in Kotlin tests
    testImplementation("io.mockk:mockk:1.14.5")
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
    sign(publishing.publications["release"])
}

// Dokka configuration for Android library documentation
tasks.dokkaJavadoc.configure {
    moduleName.set("launchdarkly-observability-android")
    moduleVersion.set(project.version.toString())
    outputDirectory.set(layout.projectDirectory.dir("docs"))

    dokkaSourceSets {
        configureEach {
            includes.from("doc-module.md")
        }
    }
}
