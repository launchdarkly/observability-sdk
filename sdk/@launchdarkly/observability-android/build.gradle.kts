plugins {
    id("io.github.gradle-nexus.publish-plugin").version("2.0.0").apply(true)
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
