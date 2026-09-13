// swift-tools-version: 6.1
// This is a Skip (https://skip.dev) package.
import Foundation
import PackageDescription

// The OTel-only `LaunchDarklyOtel` product is not published yet, so iOS builds it from a
// checkout of swift-launchdarkly-observability that sits alongside this repository. Skip
// copies this manifest into the Swift package it generates for Android, so the sibling
// path is resolved to an absolute one here rather than left relative.
let observabilityPath = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()
    .appendingPathComponent("../../../swift-launchdarkly-observability")
    .standardized.path

let package = Package(
    name: "skipapp-howdy",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "HowdySkip", type: .dynamic, targets: ["HowdySkip"]),
    ],
    dependencies: [
        .package(url: "https://source.skip.tools/skip.git", from: "1.6.35"),
        .package(url: "https://source.skip.tools/skip-fuse-ui.git", from: "1.10.5"),
        // Official LaunchDarkly iOS / Apple client SDK. Android uses the Maven artifact
        // declared in Sources/HowdySkip/Skip/skip.yml instead. Pinned to the version
        // swift-launchdarkly-observability requires.
        .package(url: "https://github.com/launchdarkly/ios-client-sdk.git", exact: "11.5.0"),
        .package(path: observabilityPath),
    ],
    targets: [
        .target(name: "HowdySkip", dependencies: [
            .product(name: "SkipFuseUI", package: "skip-fuse-ui"),
            .product(name: "LaunchDarkly", package: "ios-client-sdk", condition: .when(platforms: [.iOS, .macOS, .tvOS, .watchOS])),
            .product(name: "LaunchDarklyOtel", package: "swift-launchdarkly-observability", condition: .when(platforms: [.iOS, .tvOS])),
        ], resources: [.process("Resources")], plugins: [.plugin(name: "skipstone", package: "skip")]),
    ]
)
