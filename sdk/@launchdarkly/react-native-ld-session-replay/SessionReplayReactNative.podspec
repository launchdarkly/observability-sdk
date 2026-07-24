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
  s.source       = { :git => "https://github.com/launchdarkly/swift-launchdarkly-observability.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  s.private_header_files = "ios/**/*.h"

  # Mixing Swift and Objective-C++ in one pod requires the pod to define its own
  # Clang module so the Swift compiler emits the `-Swift.h` interop header that
  # SessionReplayReactNative.mm imports. Without this the header is not generated
  # under default static-library linking and the build fails to compile.
  s.pod_target_xcconfig = {
    "DEFINES_MODULE" => "YES"
  }

  s.dependency 'LaunchDarklySessionReplay', '~> 0.47.0'

  install_modules_dependencies(s)
end
