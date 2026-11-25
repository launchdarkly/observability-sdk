plugins {
    java
}

description = "Session replay demo for JavaFX"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// JavaFX jars carry their natives, so they are published per platform.
val javafxPlatform = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch") in listOf("aarch64", "arm64")
    when {
        os.contains("mac") -> if (arm) "mac-aarch64" else "mac"
        os.contains("win") -> "win"
        else -> if (arm) "linux-aarch64" else "linux"
    }
}

dependencies {
    implementation(project(":"))
    implementation("com.launchdarkly:launchdarkly-java-client-sdk-observability:0.1.0")
    listOf("base", "graphics", "controls").forEach {
        implementation("org.openjfx:javafx-$it:21.0.6:$javafxPlatform")
    }
}

// A task of its own name, so `./gradlew run` stays the console demo's alone.
tasks.register<JavaExec>("runJavaFxDemo") {
    group = "application"
    description = "Runs the JavaFX session replay demo."
    mainClass = "com.launchdarkly.example.clientdemo.replay.JavaFxDemoMain"
    classpath = sourceSets["main"].runtimeClasspath
}
