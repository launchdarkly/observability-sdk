plugins {
    java
}

description = "Session replay demo for SWT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// SWT's jar for this platform, which carries its natives.
val swtPlatform = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "aarch64" else "x86_64"
    when {
        os.contains("mac") -> "cocoa.macosx.$arch"
        os.contains("win") -> "win32.win32.x86_64"
        else -> "gtk.linux.$arch"
    }
}
val mac = System.getProperty("os.name").lowercase().contains("mac")

dependencies {
    implementation(project(":"))
    implementation("com.launchdarkly:launchdarkly-java-client-sdk-observability:0.1.0")
    // SWT's POMs name their natives through a variable Gradle cannot resolve, so only the jar is taken.
    implementation("org.eclipse.platform:org.eclipse.swt.$swtPlatform:3.135.0") { isTransitive = false }
}

// A task of its own name, so `./gradlew run` stays the console demo's alone.
tasks.register<JavaExec>("runSwtDemo") {
    group = "application"
    description = "Runs the SWT session replay demo."
    mainClass = "com.launchdarkly.example.clientdemo.replay.SwtDemo"
    classpath = sourceSets["main"].runtimeClasspath
    // On macOS, SWT runs only on the JVM's main thread.
    if (mac) {
        jvmArgs("-XstartOnFirstThread")
    }
}
