# Launching the example apps

This package ships two example apps:

| App             | Directory        | Architecture         | React Native |
| --------------- | ---------------- | -------------------- | ------------ |
| New arch        | `example`        | New (Fabric/Turbo)   | 0.83         |
| Legacy arch     | `example-legacy` | Legacy (bridge)      | 0.78         |

Both consume the workspace packages from local source via Metro (`metro.config.js`
maps `@launchdarkly/session-replay-react-native` and
`@launchdarkly/observability-react-native` to their `src/` entry points), so any
change you make to the library is picked up by a Metro reload — no separate
`yarn build` in the observability package is required for the examples.

## Prerequisites

Run everything from the monorepo root unless noted otherwise.

1. Install dependencies (Yarn 4 workspaces):

```bash
yarn install
```

2. Provide a LaunchDarkly mobile key (and optional endpoint overrides). Each app
   reads its own gitignored `.env`, so the two examples can target different
   environments:

```bash
# new-arch example
# sdk/@launchdarkly/react-native-ld-session-replay/example/.env
LAUNCHDARKLY_MOBILE_KEY=mob-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

# legacy example (see example-legacy/.env.example for all keys)
# sdk/@launchdarkly/react-native-ld-session-replay/example-legacy/.env
LAUNCHDARKLY_MOBILE_KEY=mob-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
LAUNCHDARKLY_OTLP_ENDPOINT=https://otel.observability.app.launchdarkly.com:4318
LAUNCHDARKLY_BACKEND_URL=https://pub.observability.app.launchdarkly.com/
```

The legacy example's `LAUNCHDARKLY_OTLP_ENDPOINT` / `LAUNCHDARKLY_BACKEND_URL`
route the JS observability telemetry to the configured environment (they fall
back to production defaults if unset). Get your key from
https://app.launchdarkly.com/settings/projects.

> Note: native session replay upload endpoints are not yet configurable from JS
> (tracked by a TODO in the Android adapter), so on-device replay capture still
> targets the production observability backend.

## New-arch example (`example`)

```bash
cd sdk/@launchdarkly/react-native-ld-session-replay/example

# start Metro (leave running in its own terminal)
yarn start
```

### iOS

```bash
# install pods (new architecture is the default)
bundle install
bundle exec pod install --project-directory=ios

# build + launch on a simulator
yarn ios
```

### Android

```bash
# start an emulator (or connect a device), then:
yarn android
```

## Legacy-arch example (`example-legacy`)

The legacy app pins `RCT_NEW_ARCH_ENABLED=0` so it builds against the React
Native bridge instead of Fabric/TurboModules.

```bash
cd sdk/@launchdarkly/react-native-ld-session-replay/example-legacy

# start Metro (leave running in its own terminal)
yarn start
```

### iOS

```bash
# pods must be installed with the legacy flag (the `pods` script sets it)
yarn pods            # RCT_NEW_ARCH_ENABLED=0 bundle exec pod install --project-directory=ios

# build + launch on a simulator
yarn ios
```

### Android

```bash
# newArchEnabled=false is set in android/gradle.properties
yarn android
```

When the app boots you should see a banner above the tabs showing the active
architecture and RN version (e.g. `Legacy Architecture · React Native 0.78.0`),
which is a quick confirmation that the native module registered correctly.

## Web

These are native React Native apps (iOS/Android only); there is no web target in
either example. To exercise the SDK on web, use the browser
`@launchdarkly/observability` / `@launchdarkly/session-replay` packages and their
own web examples instead.

## Troubleshooting

- **`Incompatible React versions` (legacy app):** make sure `yarn install` was
  run from the repo root so the workspace-scoped resolutions pin `react` to the
  version expected by RN 0.78.
- **iOS picks up the wrong architecture:** delete `ios/Pods` and
  `ios/build`, then re-run `yarn pods` (legacy) or
  `bundle exec pod install --project-directory=ios` (new arch).
- **Stale native module after editing the library:** rebuild the native app
  (`yarn ios` / `yarn android`); a Metro reload only refreshes JS.
- **JS observability changes not showing up:** restart Metro with a clean cache
  (`yarn start --reset-cache`). The examples resolve both workspace SDKs from
  `src/`; if Metro fails on OpenTelemetry subpath imports (e.g.
  `@opentelemetry/otlp-exporter-base/browser-http`), ensure `metro.config.js`
  has `unstable_enablePackageExports: true` (already set for both examples).
  As a fallback, remove the observability `sourcePackageEntries` mapping and
  run `yarn build` inside `@launchdarkly/observability-react-native` to use
  the prebundled `dist/` instead.
- **JS spans missing but native replay traces appear:** these are separate
  pipelines — JS spans go through `LDObserve` / OpenTelemetry OTLP export;
  native replay uses the session-replay native module. Check the Tracing tab
  **Session info** button (`initialized=true`) and tap **Flush** after creating
  spans. With `debug: true` in `App.tsx`, look for `[InstrumentationManager]
  Tracing initialized` in the Metro console.
