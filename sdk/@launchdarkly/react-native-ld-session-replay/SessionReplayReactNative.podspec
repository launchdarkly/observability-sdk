require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "SessionReplayReactNative"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/launchdarkly/observability-sdk.git",
                     :tag => "rn-session-replay-#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  s.private_header_files = "ios/**/*.h"

  # Pre-built XCFrameworks from swift-launchdarkly-observability.
  # Built via scripts/build-xcframeworks.sh and shipped inside the npm package.
  # This replaces the previous spm_dependency approach, eliminating the need for
  # react_native_post_install SPM support and network access at pod install time.
  #
  # LaunchDarklyObservability is a "fat" static archive that already contains all
  # internal and ObjC-only transitive deps (KSCrash, Common, NetworkStatus, etc.).
  # The remaining xcframeworks satisfy the Swift module references (@_exported import
  # LaunchDarkly / OpenTelemetryApi, plus OtlpConfiguration, ReadableLogRecord,
  # SwiftProtobuf.Message) that appear in the public .swiftinterface files.
  s.vendored_frameworks = [
    'ios/Frameworks/LaunchDarklyObservability.xcframework',
    'ios/Frameworks/LaunchDarklySessionReplay.xcframework',
    'ios/Frameworks/LaunchDarkly.xcframework',
    'ios/Frameworks/OpenTelemetryApi.xcframework',
    'ios/Frameworks/OpenTelemetrySdk.xcframework',
    'ios/Frameworks/OpenTelemetryProtocolExporterCommon.xcframework',
    'ios/Frameworks/SwiftProtobuf.xcframework',
  ]

  s.pod_target_xcconfig = { 'BUILD_LIBRARY_FOR_DISTRIBUTION' => 'YES' }

  install_modules_dependencies(s)
end
