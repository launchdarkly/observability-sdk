const path = require('path');
const {getConfig} = require('react-native-builder-bob/babel-config');
const pkg = require('../package.json');

const root = path.resolve(__dirname, '..');

module.exports = getConfig(
  {
    presets: ['module:@react-native/babel-preset'],
    plugins: [
      // Reuse the sibling `example`'s .env as the single source of truth for the
      // LaunchDarkly mobile key, so this legacy example doesn't need its own copy.
      [
        'module:react-native-dotenv',
        {
          moduleName: '@env',
          path: '../example/.env',
          // safe=false: don't enforce that .env keys match a .env.example schema.
          safe: false,
          // allowUndefined=true: missing keys resolve to undefined.
          allowUndefined: true,
        },
      ],
    ],
  },
  {root, pkg},
);
