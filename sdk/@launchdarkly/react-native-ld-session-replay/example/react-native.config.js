const path = require('path');
const pkg = require('../package.json');

module.exports = {
  project: {
    ios: {
      // RN CLI's automatic pod install requires a Gemfile and runs
      // `bundle install` + `bundle exec pod install`, which on Ruby 4.0+
      // resolves to the upstream-pinned CocoaPods 1.15.2 (kconv-broken).
      // Our scripts/run-ios.sh runs `pod install` via the Homebrew-installed
      // pod (with its own bundled Ruby) before invoking the CLI, so we
      // disable the CLI's auto-install here.
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
