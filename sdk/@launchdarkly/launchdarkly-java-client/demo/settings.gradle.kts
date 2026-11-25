pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

rootProject.name = "launchdarkly-java-client-demo-app"

// The observability plugin and its Compose library are built from source in ../plugin, so edits to
// the plugin show up here without publishing it. The dependency coordinates stay the published
// ones, so the demo still reads as a consumer would write it.
includeBuild("../plugin") {
    dependencySubstitution {
        substitute(module("com.launchdarkly:launchdarkly-java-client-sdk-observability")).using(project(":"))
        substitute(module("com.launchdarkly:launchdarkly-java-client-sdk-observability-compose")).using(project(":compose"))
    }
}

// The client-side Java SDK resolves from Maven local, then Maven Central. Pass -PsdkDir=<path to a
// java-client-sdk checkout> to build it from source instead, so edits to the SDK show up here
// without republishing.
providers.gradleProperty("sdkDir").orNull?.let { includeBuild(it) }

// Session replay demos, one per desktop toolkit the plugin records. The Swing one is the dashboard.
include("replay:javafx", "replay:swt", "replay:pivot", "replay:compose")
