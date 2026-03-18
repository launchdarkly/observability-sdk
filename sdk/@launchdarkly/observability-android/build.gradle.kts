plugins {
    id("io.github.gradle-nexus.publish-plugin").version("2.0.0").apply(true)
    id("org.jetbrains.kotlin.android").version("2.2.0").apply(false)
}

// Must be specified in root project for the gradle nexus publish plugin.
group = "com.launchdarkly"

allprojects {
    repositories {
        google()
        mavenCentral()
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
}

nexusPublishing {
    this.repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}
