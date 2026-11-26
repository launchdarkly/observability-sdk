plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

description = "Session replay demo for Compose for Desktop"

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    // Modifier.ldMask() and ldId(): the same as Android's. Built from ../plugin, like the plugin.
    implementation("com.launchdarkly:launchdarkly-java-client-sdk-observability-compose:0.1.0")
}

// A task of its own name, so `./gradlew run` stays the console demo's alone.
tasks.register<JavaExec>("runComposeDemo") {
    group = "application"
    description = "Runs the Compose for Desktop session replay demo."
    mainClass = "com.launchdarkly.example.clientdemo.replay.ComposeDemoKt"
    classpath = sourceSets["main"].runtimeClasspath
}
