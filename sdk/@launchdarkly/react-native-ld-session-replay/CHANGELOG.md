# Changelog

## [0.18.1](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.18.0...session-replay-react-native-0.18.1) (2026-07-17)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.16.0

## [0.18.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.17.0...session-replay-react-native-0.18.0) (2026-07-16)


### Features

* **observability:** dedupe feature flag exposures within a time window (web + React Native) ([#676](https://github.com/launchdarkly/observability-sdk/issues/676)) ([c34b390](https://github.com/launchdarkly/observability-sdk/commit/c34b390d68ff642e13f2863d1aa3f2732c61b70b))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.15.0

## [0.17.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.16.0...session-replay-react-native-0.17.0) (2026-07-08)


### Features

* add app_reload event with session preservation across reloads (React Native) ([#670](https://github.com/launchdarkly/observability-sdk/issues/670)) ([518bb22](https://github.com/launchdarkly/observability-sdk/commit/518bb225df0d06d7d5bf7029f38b60125e7b7624))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.14.0

## [0.16.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.15.0...session-replay-react-native-0.16.0) (2026-06-30)


### Features

* Propagate session replay frameRate, scale, and sampleRate in React Native ([#661](https://github.com/launchdarkly/observability-sdk/issues/661)) ([3cf63ab](https://github.com/launchdarkly/observability-sdk/commit/3cf63ab22071e581b02c49baabc299293abfe7f6))

## [0.15.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.14.1...session-replay-react-native-0.15.0) (2026-06-29)


### Features

* **observability-react-native:** expose LDTracer with withSpan via getTracer() ([#658](https://github.com/launchdarkly/observability-sdk/issues/658)) ([36a622a](https://github.com/launchdarkly/observability-sdk/commit/36a622a6ab2878ac7b262b51cbf557ebdba57271))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.13.0

## [0.14.1](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.14.0...session-replay-react-native-0.14.1) (2026-06-29)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.12.0

## [0.14.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.13.0...session-replay-react-native-0.14.0) (2026-06-26)


### Features

* **observability-react-native:** accept plain nested dictionaries in track ([#650](https://github.com/launchdarkly/observability-sdk/issues/650)) ([f34ec69](https://github.com/launchdarkly/observability-sdk/commit/f34ec69ab830c9486705dc68247a5f50b114accb))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.11.0

## [0.13.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.12.0...session-replay-react-native-0.13.0) (2026-06-26)


### Features

* **session-replay-react-native:** support React Native legacy architecture (RN 0.75+) ([#653](https://github.com/launchdarkly/observability-sdk/issues/653)) ([8ef4b30](https://github.com/launchdarkly/observability-sdk/commit/8ef4b30afc0d785ae25a4db2135a7064c9c32950))


### Bug Fixes

* backendurl and otlpendpoind pass through ([#648](https://github.com/launchdarkly/observability-sdk/issues/648)) ([a7b5a47](https://github.com/launchdarkly/observability-sdk/commit/a7b5a47f9aa02a44191df81a96a3a9db7eb38823))

## [0.12.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.11.1...session-replay-react-native-0.12.0) (2026-06-25)


### Features

* supporting advanced trace cases ([#645](https://github.com/launchdarkly/observability-sdk/issues/645)) ([dac45eb](https://github.com/launchdarkly/observability-sdk/commit/dac45eb6e61c63f954da1ec63aec58764bc810e1))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.10.0

## [0.11.1](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.11.0...session-replay-react-native-0.11.1) (2026-06-05)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.9.3

## [0.11.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.10.0...session-replay-react-native-0.11.0) (2026-06-03)


### Features

* Android - Rename track span event  ([#586](https://github.com/launchdarkly/observability-sdk/issues/586)) ([2f5b066](https://github.com/launchdarkly/observability-sdk/commit/2f5b0667385b59181d3e1970234fc7bcae08aa19))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.9.2

## [0.10.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.9.0...session-replay-react-native-0.10.0) (2026-05-08)


### Features

* **react-native:** add &lt;LDMask&gt; and &lt;LDUnmask&gt; wrapper components ([#535](https://github.com/launchdarkly/observability-sdk/issues/535)) ([2b25a7e](https://github.com/launchdarkly/observability-sdk/commit/2b25a7ee0db223a6770a36f21eac576c5d3ade9f))
* **react-native:** drop accessibilityIdentifiers options; mark minimumAlpha iOS-only ([#538](https://github.com/launchdarkly/observability-sdk/issues/538)) ([e3c1ba4](https://github.com/launchdarkly/observability-sdk/commit/e3c1ba4e793f3b88076a39e711410825b5b29e9b))

## [0.9.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.8.0...session-replay-react-native-0.9.0) (2026-05-06)


### Features

* **react-native:** add maskTestIDs and unmaskTestIDs masking options ([#528](https://github.com/launchdarkly/observability-sdk/issues/528)) ([3d33328](https://github.com/launchdarkly/observability-sdk/commit/3d3332883e98874aca7894d0af096776a7698e43))

## [0.8.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.7.0...session-replay-react-native-0.8.0) (2026-05-01)


### Features

* **android:** update session replay masking precedence rules ([#518](https://github.com/launchdarkly/observability-sdk/issues/518)) ([4299d1e](https://github.com/launchdarkly/observability-sdk/commit/4299d1e65bdf7d3056b92631ba7eb1e36e3e96f5))

## [0.7.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.6.0...session-replay-react-native-0.7.0) (2026-04-29)


### Features

* RN decrease Kotlin to 2.0.21 and increase JVM to 11 ([#511](https://github.com/launchdarkly/observability-sdk/issues/511)) ([1593510](https://github.com/launchdarkly/observability-sdk/commit/15935101de0163f2e47109ef35e1cf9369db9599))

## [0.6.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.5.1...session-replay-react-native-0.6.0) (2026-04-28)


### Features

* Disable CrashReporting for RN iOS and Android ([#504](https://github.com/launchdarkly/observability-sdk/issues/504)) ([a9907a7](https://github.com/launchdarkly/observability-sdk/commit/a9907a70c6678fa24e7e97834023fcc693a070d0))

## [0.5.1](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.5.0...session-replay-react-native-0.5.1) (2026-04-24)


### Bug Fixes

* **react-native:** propagate afterIdentify propagation in iOS session replay ([#501](https://github.com/launchdarkly/observability-sdk/issues/501)) ([b6ec2b1](https://github.com/launchdarkly/observability-sdk/commit/b6ec2b1c0ae8bab76526bb988d39d8f20342fd30))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.9.1

## [0.5.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.4.3...session-replay-react-native-0.5.0) (2026-04-24)


### Features

* **react-native:** Propagate LDContext to Android session replay via afterIdentify hook ([#495](https://github.com/launchdarkly/observability-sdk/issues/495)) ([8179a8e](https://github.com/launchdarkly/observability-sdk/commit/8179a8e5f632fdad86cc52f2acdf19bd51acff2a))

## [0.4.3](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.4.2...session-replay-react-native-0.4.3) (2026-04-22)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.9.0

## [0.4.2](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.4.1...session-replay-react-native-0.4.2) (2026-04-22)


### Bug Fixes

* **deps:** address dependabot security alerts across SDK manifests ([#478](https://github.com/launchdarkly/observability-sdk/issues/478)) ([02f6a7c](https://github.com/launchdarkly/observability-sdk/commit/02f6a7ce6c5d5dbb22f8cde81647c3e4deb05ab6))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.8.1

## [0.4.1](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.4.0...session-replay-react-native-0.4.1) (2026-04-18)


### Bug Fixes

* [SDK-2197] manually register main Activity with session replay ([#475](https://github.com/launchdarkly/observability-sdk/issues/475)) ([75fd6e7](https://github.com/launchdarkly/observability-sdk/commit/75fd6e70e9809a027272bc1873765a5a1f201d8a))

## [0.4.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.3.0...session-replay-react-native-0.4.0) (2026-04-17)


### Features

* [SDK-2121] implement session replay for react native android ([#456](https://github.com/launchdarkly/observability-sdk/issues/456)) ([a5af1d8](https://github.com/launchdarkly/observability-sdk/commit/a5af1d8368fc06ffbef9b486f4b40cbfd7390ef3))
* [SDK-2190] add dialogs to the RN session replay example app ([#471](https://github.com/launchdarkly/observability-sdk/issues/471)) ([c677962](https://github.com/launchdarkly/observability-sdk/commit/c6779627f17bdb161b4fb79092dd6743ad54f9bf))

## [0.3.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.2.2...session-replay-react-native-0.3.0) (2026-03-26)


### Features

* **@launchdarkly/session-replay-react-native:** use cocoapods for native session replay ([#434](https://github.com/launchdarkly/observability-sdk/issues/434)) ([41988e1](https://github.com/launchdarkly/observability-sdk/commit/41988e196b02901964efdad0e25f892d5ba55fc4))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.8.0

## [0.2.2](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.2.1...session-replay-react-native-0.2.2) (2026-03-05)


### Bug Fixes

* ldclient dependencies ([#407](https://github.com/launchdarkly/observability-sdk/issues/407)) ([65a5e6a](https://github.com/launchdarkly/observability-sdk/commit/65a5e6a1999c9e66c7f4011f512d17de256f919c))


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * @launchdarkly/observability-react-native bumped to 0.7.1

## [0.2.1](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.2.0...session-replay-react-native-0.2.1) (2026-02-27)


### Bug Fixes

* correct react native session replay build step ([#399](https://github.com/launchdarkly/observability-sdk/issues/399)) ([a6c84b8](https://github.com/launchdarkly/observability-sdk/commit/a6c84b8384e25fe8a6b4af06890a52fb4bf8e82f))

## [0.2.0](https://github.com/launchdarkly/observability-sdk/compare/session-replay-react-native-0.1.0...session-replay-react-native-0.2.0) (2026-02-23)


### Features

* add session replay support for react native ([#357](https://github.com/launchdarkly/observability-sdk/issues/357)) ([7f6ed30](https://github.com/launchdarkly/observability-sdk/commit/7f6ed3041ed641be47b1f5c2e0d30d4ef1727bd0))


### Bug Fixes

* **session-replay-react-native:** session replay start with options enabled/disabled ([#386](https://github.com/launchdarkly/observability-sdk/issues/386)) ([d012adc](https://github.com/launchdarkly/observability-sdk/commit/d012adc4fdac573c7a4816e94f037aca02cc7c63))
