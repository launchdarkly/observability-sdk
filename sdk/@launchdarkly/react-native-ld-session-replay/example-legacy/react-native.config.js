const path = require('path');
const pkg = require('../package.json');

module.exports = {
  project: {
    ios: {
      // Pods for this example must be installed with RCT_NEW_ARCH_ENABLED=0 to
      // stay on the legacy architecture, so we install them manually rather than
      // letting the build trigger a default (new-arch) `pod install`.
      automaticPodsInstallation: false,
    },
  },
  dependencies: {
    [pkg.name]: {
      root: path.join(__dirname, '..'),
      platforms: {
        // Codegen script incorrectly fails without this
        // So we explicitly specify the platforms with empty object
        ios: {},
        android: {},
      },
    },
  },
};
