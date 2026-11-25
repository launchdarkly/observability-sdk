plugins {
    application
}

description = "Sample app for the LaunchDarkly client-side Java SDK"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    // The client-side Java SDK isn't published yet. Install it from a checkout of
    // java-client-sdk with ./gradlew :launchdarkly-java-client-sdk:publishToMavenLocal, or pass
    // -PsdkDir=<checkout> to build it from source (see settings.gradle.kts).
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.launchdarkly:launchdarkly-java-client-sdk:0.1.0")
    // LaunchDarkly Observability for the client SDK, built from ../plugin (see settings.gradle.kts).
    implementation("com.launchdarkly:launchdarkly-java-client-sdk-observability:0.1.0")

    // The SDK logs through launchdarkly-logging; the demo routes that to SLF4J,
    // so it lands in the same Logback console output as everything else.
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("ch.qos.logback:logback-classic:1.6.3")
}

application {
    mainClass = "com.launchdarkly.example.clientdemo.DemoApp"
}

/**
 * Reads a `.env` file into a map, so `./gradlew run` needs no shell preamble.
 * Blank lines and `#` comments are skipped; surrounding quotes are stripped.
 */
fun dotEnv(): Map<String, String> {
    val envFile = file(".env")
    if (!envFile.exists()) {
        return emptyMap()
    }
    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val name = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            name to value
        }
}

// The replay demos read the same .env as the other demos, and run from here, where it is.
subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
    tasks.withType<JavaExec>().configureEach {
        environment(dotEnv())
        workingDir = rootDir
    }
}

tasks.named<JavaExec>("run") {
    // Without this the demo's interactive commands cannot be typed under
    // `gradlew run`, since Gradle gives the process an empty stdin.
    standardInput = System.`in`
    environment(dotEnv())
}

tasks.register<JavaExec>("doctor") {
    group = "application"
    description = "Prints diagnostics for the connection and the configured flags, then exits."
    mainClass = application.mainClass
    classpath = sourceSets["main"].runtimeClasspath
    environment(dotEnv())
    args("doctor")
}

tasks.register<JavaExec>("runDashboard") {
    group = "application"
    description = "Runs the Swing dashboard, whose components are each behind a flag."
    mainClass = "com.launchdarkly.example.clientdemo.dashboard.DashboardApp"
    classpath = sourceSets["main"].runtimeClasspath
    environment(dotEnv())
}

/**
 * One runnable jar holding both demos and every dependency, including the
 * unreleased SDK, so it runs on any machine with Java 21:
 *
 *   java -jar build/libs/launchdarkly-java-client-demo-app-all.jar           (dashboard)
 *   java -jar build/libs/launchdarkly-java-client-demo-app-all.jar console   (console demo)
 *
 * Credentials are never bundled: .env is not on the classpath, and the app
 * reads a .env from the directory it is started in.
 */
val fatJar = tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Builds a single runnable jar with both demos and all dependencies."
    archiveClassifier = "all"
    manifest {
        attributes("Main-Class" to "com.launchdarkly.example.clientdemo.Launcher")
    }
    from(sourceSets["main"].output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    }) {
        // Signatures from dependency jars would not match the merged jar.
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
        exclude("META-INF/versions/*/module-info.class", "module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("assemble") {
    dependsOn(fatJar)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
