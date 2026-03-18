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
    plugins {
        id("com.getkeepsafe.dexcount") version "4.0.0"
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

include(":launchdarkly-android-client-sdk")
project(":launchdarkly-android-client-sdk").projectDir =
    file("/Users/abelonogov/work/android-client-sdk/launchdarkly-android-client-sdk")

include(":shared-test-code")
project(":shared-test-code").projectDir =
    file("/Users/abelonogov/work/android-client-sdk/shared-test-code")
