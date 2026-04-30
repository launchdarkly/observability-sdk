# Podfile helpers for @launchdarkly/session-replay-react-native consumers.
#
# These helpers wrap workarounds that are required to build the LaunchDarkly
# Session Replay React Native SDK with the React Native + Xcode + CocoaPods
# combinations we currently support. Consumers integrate them by adding two
# lines to their Podfile (see README.md for full usage):
#
#   require_relative '../node_modules/@launchdarkly/session-replay-react-native/scripts/podfile_setup'
#
#   launchdarkly_sr_pre_install                            # before the `target` block
#
#   target 'MyApp' do
#     # ...
#     post_install do |installer|
#       react_native_post_install(installer, ...)
#       launchdarkly_sr_post_install(installer)            # last thing inside post_install
#     end
#   end
#
# Each helper is a no-op if its workaround is not needed; calling them is
# always safe.

# Top-level (pre-target) declarations.
#
# CocoaPods 1.16.x + Xcode 26 sometimes fails to write the
# `Pods/Headers/Public/<Pod>/<Pod>.modulemap` symlinks for Swift-only static
# pods that are pulled in transitively. Re-declaring those pods here as
# top-level dependencies with `:modular_headers => true` forces CocoaPods to
# generate the modulemap, which fixes:
#
#     Module map file '.../SwiftProtobuf.modulemap' not found
#     Unable to find module dependency: 'SwiftProtobuf'
#
# We can't switch the entire project to `use_modular_headers!` because RN core
# pods (React-RuntimeCore + React-jsitooling) both declare `module
# react_runtime` under that mode and collide.
def launchdarkly_sr_pre_install
  pod 'SwiftProtobuf', :modular_headers => true
  pod 'SocketRocket',  :modular_headers => true
end

# Post-install build-setting overrides.
#
# Workaround for RN 0.83 + Xcode 26 incompatibility: the new Xcode 26 build
# engine's explicit-modules path triggers cascading
#
#     Could not build module 'CoreFoundation'
#     Could not build module 'Foundation'
#     Could not build module '_DarwinFoundation1'
#
# errors in React-RuntimeHermes (C++) and other mixed-language pods. Disabling
# the explicit-modules build paths (Swift + Clang) falls back to the older
# implicit-modules path, which works with the SDK shipped in Xcode 26.
#
# We also bump any pods stuck on IPHONEOS_DEPLOYMENT_TARGET < 15.1 up to 15.1
# (matches `min_ios_version_supported` in current RN templates) to silence
# deprecation warnings.
#
# References:
#   https://github.com/react-native-community/discussions-and-proposals/discussions/978
def launchdarkly_sr_post_install(installer, min_ios_deployment_target: '15.1')
  min_target_f = min_ios_deployment_target.to_f

  installer.pods_project.targets.each do |target|
    target.build_configurations.each do |bc|
      bc.build_settings['SWIFT_ENABLE_EXPLICIT_MODULES'] = 'NO'
      bc.build_settings['_EXPERIMENTAL_CLANG_EXPLICIT_MODULES'] = 'NO'
      bc.build_settings['CLANG_ENABLE_EXPLICIT_MODULES'] = 'NO'

      cur = bc.build_settings['IPHONEOS_DEPLOYMENT_TARGET']
      if cur.nil? || cur.to_f < min_target_f
        bc.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = min_ios_deployment_target
      end
    end
  end

  # The host app project also needs the explicit-modules opt-out so that
  # React-RuntimeHermes consumers (Hermes runtime headers, etc.) inherit the
  # implicit-modules build path.
  installer.aggregate_targets.each do |agg|
    agg.user_project.targets.each do |t|
      t.build_configurations.each do |bc|
        bc.build_settings['SWIFT_ENABLE_EXPLICIT_MODULES'] = 'NO'
        bc.build_settings['_EXPERIMENTAL_CLANG_EXPLICIT_MODULES'] = 'NO'
        bc.build_settings['CLANG_ENABLE_EXPLICIT_MODULES'] = 'NO'
      end
    end
    agg.user_project.save
  end
end
