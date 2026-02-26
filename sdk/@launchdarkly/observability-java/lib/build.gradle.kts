plugins {
    `java-library`
    `maven-publish`
    signing
}

val releaseVersion = version.toString()

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    // LaunchDarkly SDK - compileOnly so users provide their own version
    compileOnly("com.launchdarkly:launchdarkly-java-server-sdk:7.12.0")

    // LaunchDarkly OTel tracing hook
    implementation("com.launchdarkly:launchdarkly-java-server-sdk-otel:0.2.0")

    // OpenTelemetry
    implementation("io.opentelemetry:opentelemetry-api:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-logs:1.51.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.51.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.11.0")

    // Testing
    testImplementation("com.launchdarkly:launchdarkly-java-server-sdk:7.12.0")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.launchdarkly"
            artifactId = "launchdarkly-observability-java"
            version = releaseVersion

            from(components["java"])

            pom {
                name.set("LaunchDarkly Observability Java SDK")
                description.set(
                    "Official LaunchDarkly Observability Java SDK for use with the LaunchDarkly Java Server SDK."
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
        }
    }
}

signing {
    sign(publishing.publications["release"])
}
