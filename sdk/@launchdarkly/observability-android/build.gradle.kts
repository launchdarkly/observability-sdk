plugins {
    id("io.github.gradle-nexus.publish-plugin").version("2.0.0").apply(true)
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = uri("https://jitpack.io")) {
            content {
                includeGroup("com.github.aasitnikov")
            }
        }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.1")
        // Use locally built fat-aar plugin (patched for duplicate artifact handling)
        classpath(files(rootProject.file("fat-aar-gradle-plugin.jar")))
    }
}

// Must be specified in root project for the gradle nexus publish plugin.
group = "com.launchdarkly"

nexusPublishing {
    this.repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}
