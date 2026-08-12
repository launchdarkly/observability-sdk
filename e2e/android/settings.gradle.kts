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
        google()
        mavenCentral()
        maven(url = "https://maven.mozilla.org/maven2/")
    }
}

rootProject.name = "AndroidObservability"
include(":app")
include(":observability-android")
project(":observability-android").projectDir = file("../../sdk/@launchdarkly/observability-android/lib")

// The full SDK is split across two published artifacts, and `lib` depends on `otel` by project
// path, so this build has to know both. The path must stay `:otel` for that dependency to resolve.
include(":otel")
project(":otel").projectDir = file("../../sdk/@launchdarkly/observability-android/otel")
