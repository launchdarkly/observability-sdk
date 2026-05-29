// swift-tools-version: 5.9
import PackageDescription
import Foundation

func isTruthy(_ value: String?) -> Bool {
    switch value?.lowercased() {
    case "true", "1", "yes":
        return true
    default:
        return false
    }
}

let useLocalNativeSdk = isTruthy(ProcessInfo.processInfo.environment["LD_USE_LOCAL_NATIVE"])
let swiftObservabilityDependency: Package.Dependency = if useLocalNativeSdk {
    .package(
        path: ProcessInfo.processInfo.environment["LD_SWIFT_OBSERVABILITY_PATH"]
            ?? "../../../../../../../../swift-launchdarkly-observability"
    )
} else {
    .package(
        url: "https://github.com/launchdarkly/swift-launchdarkly-observability.git",
        .upToNextMinor(from: "0.36.0")
    )
}

let package = Package(
    name: "launchdarkly_flutter_session_replay",
    platforms: [
        .iOS("14.0")
    ],
    products: [
        .library(
            name: "launchdarkly-flutter-session-replay",
            targets: ["launchdarkly_flutter_session_replay"]
        )
    ],
    dependencies: [
        .package(name: "FlutterFramework", path: "../FlutterFramework"),
        swiftObservabilityDependency,
        .package(url: "https://github.com/launchdarkly/ios-client-sdk.git", exact: "11.1.1"),
    ],
    targets: [
        .target(
            name: "launchdarkly_flutter_session_replay",
            dependencies: [
                .product(name: "FlutterFramework", package: "FlutterFramework"),
                .product(name: "LaunchDarkly", package: "ios-client-sdk"),
                .product(name: "LaunchDarklyObservability", package: "swift-launchdarkly-observability"),
                .product(name: "LaunchDarklySessionReplay", package: "swift-launchdarkly-observability"),
            ]
        )
    ]
)
