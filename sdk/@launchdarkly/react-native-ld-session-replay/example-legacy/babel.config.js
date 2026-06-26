const path = require('path');
const {getConfig} = require('react-native-builder-bob/babel-config');
const pkg = require('../package.json');

const root = path.resolve(__dirname, '..');

module.exports = getConfig(
  {
    presets: ['module:@react-native/babel-preset'],
    plugins: [
      // Reads this example's own .env (see .env.example) so the legacy app can
      // point at a different environment (e.g. staging) than the new-arch example.
      [
        'module:react-native-dotenv',
        {
          moduleName: '@env',
          path: '.env',
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
