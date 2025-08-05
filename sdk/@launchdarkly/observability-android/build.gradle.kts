plugins {
    id("io.github.gradle-nexus.publish-plugin").version("2.0.0").apply(true)
}

nexusPublishing {
    this.repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username = System.getenv("CENTRAL_USERNAME")
            password = System.getenv("CENTRAL_PASSWORD")
        }
    }
}