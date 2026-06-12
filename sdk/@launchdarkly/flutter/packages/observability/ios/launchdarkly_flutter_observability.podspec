Pod::Spec.new do |s|
  s.name             = 'launchdarkly_flutter_observability'
  s.version          = '0.0.0'
  s.summary          = 'The LaunchDarkly Observability and Session Replay plugin for Flutter.'
  s.description      = <<-DESC
The LaunchDarkly Observability and Session Replay plugin for Flutter.
                       DESC
  s.homepage         = 'https://github.com/launchdarkly/observability-sdk'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'LaunchDarkly' => 'support@launchdarkly.com' }
  s.source           = { :http => 'https://github.com/launchdarkly/observability-sdk' }
  s.source_files     = 'launchdarkly_flutter_observability/Sources/launchdarkly_flutter_observability/**/*'
  s.dependency 'Flutter'

  # Native dependencies mirrored from the LaunchDarkly mobile SDKs (the same
  # stack used by sdk/@launchdarkly/react-native-ld-session-replay and the
  # .NET MAUI bridge in sdk/@launchdarkly/mobile-dotnet).
  s.dependency 'LaunchDarklyObservability', '~> 0.43.0'
  s.dependency 'LaunchDarklySessionReplay', '~> 0.43.0'

  s.platform         = :ios, '14.0'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
  s.swift_version    = '5.0'
end
