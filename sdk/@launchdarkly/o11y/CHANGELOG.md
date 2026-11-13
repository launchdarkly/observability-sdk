# Changelog

## [1.6.1](https://github.com/launchdarkly/observability-sdk/compare/o11y-1.6.0...o11y-1.6.1) (2026-11-13)


### Bug Fixes

* remediate next, react-server-dom-webpack, bouncycastle, anyio, handlebars and dependency advisories ([#757](https://github.com/launchdarkly/observability-sdk/issues/757)) ([08b8067](https://github.com/launchdarkly/observability-sdk/commit/08b8067e323012d3db934be5ddda2345aeb22d37))

## [1.6.0](https://github.com/launchdarkly/observability-sdk/compare/o11y-1.5.0...o11y-1.6.0) (2026-11-06)


### Features

* **o11y:** capture UTM query parameters and landing referrer as attributes ([#877](https://github.com/launchdarkly/observability-sdk/issues/877)) ([f32637b](https://github.com/launchdarkly/observability-sdk/commit/f32637baafaf0da635c5bb50696164d548169515))


### Bug Fixes

* **o11y:** report @launchdarkly/o11y as telemetry.distro.name ([#879](https://github.com/launchdarkly/observability-sdk/issues/879)) ([7f339e1](https://github.com/launchdarkly/observability-sdk/commit/7f339e18bf8db720cb9ee4fe2d271f45c65d2a7a))

## [1.5.0](https://github.com/launchdarkly/observability-sdk/compare/o11y-1.4.1...o11y-1.5.0) (2026-11-03)


### Features

* **o11y:** add otel.onHttpRequestSpan hook for auto fetch/XHR spans ([#872](https://github.com/launchdarkly/observability-sdk/issues/872)) ([4387ead](https://github.com/launchdarkly/observability-sdk/commit/4387eadbeddfbdd6f2449d81c906b63f42c1736d))
* **o11y:** add setAttributes/removeAttributes and stamp context keys on every span ([#855](https://github.com/launchdarkly/observability-sdk/issues/855)) ([c086c84](https://github.com/launchdarkly/observability-sdk/commit/c086c846eb1ea0832f4337885c0064004a2c2de4))
* **o11y:** link a new browser session to the one it replaces ([#857](https://github.com/launchdarkly/observability-sdk/issues/857)) ([cf5ccbe](https://github.com/launchdarkly/observability-sdk/commit/cf5ccbe5374983262a9b42783bef791cec9b1497))


### Bug Fixes

* **o11y:** don't count a full snapshot's bytes toward the next snapshot ([#874](https://github.com/launchdarkly/observability-sdk/issues/874)) ([0ca2e72](https://github.com/launchdarkly/observability-sdk/commit/0ca2e72da05567fa1b53205a5406b9a6dc4de733))

## [1.4.1](https://github.com/launchdarkly/observability-sdk/compare/o11y-1.4.0...o11y-1.4.1) (2026-10-29)


### Bug Fixes

* **o11y:** keep once listeners re-addable when user-interaction throttles ([#867](https://github.com/launchdarkly/observability-sdk/issues/867)) ([38aa3cf](https://github.com/launchdarkly/observability-sdk/commit/38aa3cff6baed05351950b89325002b02ef4ef3a))

## [1.4.0](https://github.com/launchdarkly/observability-sdk/compare/o11y-1.3.1...o11y-1.4.0) (2026-10-28)


### Features

* **o11y:** add sessionCookieDomain option and session docs ([#848](https://github.com/launchdarkly/observability-sdk/issues/848)) ([4ac4ccb](https://github.com/launchdarkly/observability-sdk/commit/4ac4ccb24b270f910c20b6e434569e507405e510))
* **o11y:** deprecate H.addSessionFeedback and ReportDialog ([#843](https://github.com/launchdarkly/observability-sdk/issues/843)) ([6d0273d](https://github.com/launchdarkly/observability-sdk/commit/6d0273d21cec5fba528f74dae89db53d38126f2a))


### Bug Fixes

* **o11y:** buffer telemetry until start() with manualStart ([#846](https://github.com/launchdarkly/observability-sdk/issues/846)) ([b851d78](https://github.com/launchdarkly/observability-sdk/commit/b851d7832bd5905d77c9ca2d41a2de6064ed364b))
* **o11y:** export metrics with delta temporality ([#845](https://github.com/launchdarkly/observability-sdk/issues/845)) ([22e7201](https://github.com/launchdarkly/observability-sdk/commit/22e7201d29eaefe22c286272fd250a07dce421ff))
* **o11y:** refresh observe-only sessions on telemetry activity ([#847](https://github.com/launchdarkly/observability-sdk/issues/847)) ([7799eb3](https://github.com/launchdarkly/observability-sdk/commit/7799eb368a2ca1d3dda01e8517d650ccfbe6f450))

## [1.3.1](https://github.com/launchdarkly/observability-sdk/compare/o11y-1.3.0...o11y-1.3.1) (2026-10-21)


### Bug Fixes

* **o11y:** keep LaunchDarkly SDK and SDK-internal output out of customer logs ([#835](https://github.com/launchdarkly/observability-sdk/issues/835)) ([fef617d](https://github.com/launchdarkly/observability-sdk/commit/fef617df3c44db9b0d04a833f82dc77c6af7e764))

## [1.3.0](https://github.com/launchdarkly/observability-sdk/compare/o11y-1.2.0...o11y-1.3.0) (2026-10-15)


### Features

* **o11y:** add maskPiiText option ([#832](https://github.com/launchdarkly/observability-sdk/issues/832)) ([c2f48ec](https://github.com/launchdarkly/observability-sdk/commit/c2f48ece917160ca29d4b4dd92ecdae5c7e88cfa))


### Bug Fixes

* **o11y:** cover mid-sheet CSS-in-JS recovery in RecordSDK ([#829](https://github.com/launchdarkly/observability-sdk/issues/829)) ([fbe957c](https://github.com/launchdarkly/observability-sdk/commit/fbe957c3f661cbceb148790fd792ba94450e7eec))

## [1.2.0](https://github.com/launchdarkly/observability-sdk/compare/o11y-1.1.26...o11y-1.2.0) (2026-10-09)


### Features

* **@launchdarkly/session-replay-react-native:** use cocoapods for native session replay ([#434](https://github.com/launchdarkly/observability-sdk/issues/434)) ([41988e1](https://github.com/launchdarkly/observability-sdk/commit/41988e196b02901964efdad0e25f892d5ba55fc4))
* add @launchdarkly/o11y as the shared browser foundation, publish highlight.run as a thin alias ([#756](https://github.com/launchdarkly/observability-sdk/issues/756)) ([6d938c4](https://github.com/launchdarkly/observability-sdk/commit/6d938c48eab054b67a9c1cfc8120445b9d11cd4d))
