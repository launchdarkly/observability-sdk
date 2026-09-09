pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        // Resolves the locally built launchdarkly-android-client-sdk, published with
        // `./gradlew publishToMavenLocal` from a checkout of that repository.
        mavenLocal()
        google()
        mavenCentral()
        maven(url = "https://maven.mozilla.org/maven2/")
    }
}

rootProject.name = "AndroidObservability"
include(":app")
include(":observability-android")
project(":observability-android").projectDir = file("../../sdk/@launchdarkly/observability-android/lib")
