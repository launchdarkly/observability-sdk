// swift-tools-version: 6.1
import PackageDescription

let package = Package(
    name: "swift-launchdarkly-observability",
    platforms: [.iOS(.v16)],
    products: [
        .library(
            name: "LaunchDarklyObservability",
            targets: ["Client", "LaunchDarklyObservability", "ObservabilityPlugins"]),
    ],
    dependencies: [
        .package(url: "https://github.com/open-telemetry/opentelemetry-swift", from: "2.0.0"),
        .package(url: "https://github.com/launchdarkly/ios-client-sdk.git", from: "9.15.0"),
    ],
    targets: [
        .target(name: "Shared"),
        .testTarget(
            name: "SharedTests",
            dependencies: [
                "Shared"
            ]
        ),
        .target(
            name: "ObserveAPI",
            dependencies: [
                .product(name: "OpenTelemetryApi", package: "opentelemetry-swift"),
                .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift"),
            ]
        ),
        .target(
            name: "Instrumentation",
            dependencies: [
                .product(name: "OpenTelemetryApi", package: "opentelemetry-swift"),
                .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift"),
                .product(name: "OpenTelemetryProtocolExporterHTTP", package: "opentelemetry-swift"),
                .product(name: "StdoutExporter", package: "opentelemetry-swift"),
            ]
        ),
        .testTarget(
            name: "InstrumentationTests",
            dependencies: ["Instrumentation"]
        ),
        .target(
            name: "LaunchDarklyObservability",
            dependencies: [
                "ObserveAPI",
                "Client",
                .product(name: "OpenTelemetryApi", package: "opentelemetry-swift"),
                .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift"),
            ]
        ),
        .testTarget(
            name: "LaunchDarklyObservabilityTests",
            dependencies: ["LaunchDarklyObservability"]
        ),
        .target(
            name: "Client",
            dependencies: [
                "Shared",
                "ObserveAPI",
                "Instrumentation",
                .product(name: "OpenTelemetryApi", package: "opentelemetry-swift"),
                .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift"),
                .product(name: "OpenTelemetryProtocolExporterHTTP", package: "opentelemetry-swift"),
                .product(name: "StdoutExporter", package: "opentelemetry-swift"),
                .product(name: "ResourceExtension", package: "opentelemetry-swift"),
            ]
        ),
        .target(
            name: "ObservabilityPlugins",
            dependencies: [
                "Client",
                "LaunchDarklyObservability",
                "Instrumentation",
                "Shared",
                .product(name: "ResourceExtension", package: "opentelemetry-swift"),
                .product(name: "OpenTelemetryApi", package: "opentelemetry-swift"),
                .product(name: "LaunchDarkly", package: "ios-client-sdk"),
            ]
        )
    ]
)
