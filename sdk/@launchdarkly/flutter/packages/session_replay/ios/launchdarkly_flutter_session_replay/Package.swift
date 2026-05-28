// swift-tools-version: 5.9
import PackageDescription

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
        .package(url: "https://github.com/launchdarkly/swift-launchdarkly-observability.git", .upToNextMinor(from: "0.35.0")),
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
