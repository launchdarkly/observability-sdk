const path = require('path');
const { getDefaultConfig } = require('@react-native/metro-config');
const { withMetroConfig } = require('react-native-monorepo-config');
// Symbols Id Lane symbolication: stamps every release bundle with a
// deterministic symbols id derived from its composed source map, which the SDK
// reports and `ldcli symbols upload` keys the map by. See README.
const {
  withLaunchDarklySymbolsId,
} = require('@launchdarkly/observability-react-native/metro');

const root = path.resolve(__dirname, '..');

// The example imports sibling workspace packages (e.g.
// @launchdarkly/observability-react-native and @launchdarkly/observability-shared),
// which live outside `root`. Metro must watch their real paths to resolve the
// workspace symlinks; the sdk/@launchdarkly directory covers them all without
// dragging in the much larger rrweb/ and e2e/ trees.
const sdkPackages = path.resolve(__dirname, '../..');
const monorepoNodeModules = path.resolve(__dirname, '../../../../node_modules');
const observabilityNodeModules = path.resolve(
  __dirname,
  '../../observability-react-native/node_modules'
);

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = withMetroConfig(getDefaultConfig(__dirname), {
  root,
  dirname: __dirname,
});

config.watchFolders = [
  ...(config.watchFolders || []),
  sdkPackages,
  monorepoNodeModules,
];

config.resolver.nodeModulesPaths = [
  ...(config.resolver.nodeModulesPaths || []),
  monorepoNodeModules,
  observabilityNodeModules,
];

// This monorepo has SEVERAL different react-native versions installed (the root
// has 0.76.x, @launchdarkly/observability-react-native ships its own 0.79.x as a
// dev dependency, the example builds against 0.83.x, etc.). If any package
// resolves react / react-native from its own copy, the bundle ends up with a
// duplicate JS runtime whose TurboModule registry isn't wired to the native
// binary — producing "getEnforcing(...) could not be found" for both our module
// AND core modules like PlatformConstants (used by AppState in the observability
// plugin). Force every react / react-native import, from anywhere in the graph,
// to resolve to the example's single copy.
const forcedSingletons = {
  'react': path.resolve(__dirname, 'node_modules/react'),
  'react-native': path.resolve(__dirname, 'node_modules/react-native'),
};

// RN 0.83 honours package `exports`, but force source anyway so both workspace
// packages always track live TypeScript during SDK development.
const sourcePackageEntries = {
  '@launchdarkly/session-replay-react-native': path.resolve(
    __dirname,
    '../src/index.tsx'
  ),
  '@launchdarkly/observability-react-native': path.resolve(
    __dirname,
    '../../observability-react-native/src/index.ts'
  ),
};

const defaultResolveRequest = config.resolver.resolveRequest;
config.resolver.resolveRequest = (context, moduleName, platform) => {
  const sourceEntry = sourcePackageEntries[moduleName];
  if (sourceEntry) {
    return context.resolveRequest(context, sourceEntry, platform);
  }
  if (
    moduleName === '@opentelemetry/otlp-exporter-base/browser-http' ||
    moduleName.startsWith('@opentelemetry/otlp-exporter-base/')
  ) {
    return context.resolveRequest(
      {
        ...context,
        unstable_enablePackageExports: true,
        unstable_conditionNames: [
          'browser',
          'require',
          'react-native',
          'default',
        ],
      },
      moduleName,
      platform
    );
  }
  for (const [name, target] of Object.entries(forcedSingletons)) {
    if (moduleName === name || moduleName.startsWith(`${name}/`)) {
      return context.resolveRequest(
        context,
        target + moduleName.slice(name.length),
        platform
      );
    }
  }
  return (defaultResolveRequest || context.resolveRequest)(
    context,
    moduleName,
    platform
  );
};

module.exports = withLaunchDarklySymbolsId(config);
