plugins {
    java
}

description = "Session replay demo for Apache Pivot"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":"))
    implementation("com.launchdarkly:launchdarkly-java-client-sdk-observability:0.1.0")
    // Pivot's last release, from 2017; the project was retired in 2025. Terra is its theme.
    implementation("org.apache.pivot:pivot-core:2.0.5")
    implementation("org.apache.pivot:pivot-wtk:2.0.5")
    implementation("org.apache.pivot:pivot-wtk-terra:2.0.5")
}

// A task of its own name, so `./gradlew run` stays the console demo's alone.
tasks.register<JavaExec>("runPivotDemo") {
    group = "application"
    description = "Runs the Apache Pivot session replay demo."
    mainClass = "com.launchdarkly.example.clientdemo.replay.PivotDemo"
    classpath = sourceSets["main"].runtimeClasspath
}
