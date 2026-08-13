// swift-tools-version: 6.1
// This is a Skip (https://skip.dev) package.
import Foundation
import PackageDescription

// The OTel-only `LaunchDarklyOtel` product is not published yet, so iOS builds it from a
// checkout of swift-launchdarkly-observability. Skip copies this manifest into the Swift
// package it generates for Android, so a relative path would not survive; override
// `LD_SWIFT_OBSERVABILITY_PATH` if the checkout lives elsewhere.
let observabilityPath = ProcessInfo.processInfo.environment["LD_SWIFT_OBSERVABILITY_PATH"]
    ?? FileManager.default.homeDirectoryForCurrentUser
        .appendingPathComponent("flutter/swift-launchdarkly-observability").path

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
        .package(url: "https://github.com/launchdarkly/ios-client-sdk.git", exact: "11.4.0-beta.1"),
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
