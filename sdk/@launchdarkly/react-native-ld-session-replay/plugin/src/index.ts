import { ConfigPlugin, withXcodeProject } from '@expo/config-plugins';

/**
 * Expo Config Plugin for @launchdarkly/session-replay-react-native.
 *
 * Patches the Xcode project so that dynamic Swift frameworks vendored by the
 * pod (LaunchDarklyObservability.xcframework, LaunchDarklySessionReplay.xcframework)
 * are embedded correctly in the app bundle.
 *
 * Usage in app.json / app.config.js:
 *   "plugins": ["@launchdarkly/session-replay-react-native"]
 */
const withSessionReplay: ConfigPlugin = (config) => {
  return withXcodeProject(config, (projectConfig) => {
    const project = projectConfig.modResults;

    const buildConfigs = project.pbxBuildConfigurationSection();
    for (const [, buildConfig] of Object.entries(buildConfigs)) {
      if (
        typeof buildConfig !== 'object' ||
        !buildConfig.buildSettings
      ) {
        continue;
      }
      // Required when embedding dynamic Swift frameworks into an app that
      // may not otherwise include the Swift standard libraries.
      buildConfig.buildSettings['ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES'] =
        'YES';
    }

    return projectConfig;
  });
};

export default withSessionReplay;
