pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "8.13.2"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()

        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "LDObserve"
include(":LDObserve")

includeBuild("../../../observability-android") {
    dependencySubstitution {
        substitute(module("com.launchdarkly:launchdarkly-observability-android"))
            .using(project(":lib"))
    }
}
