# Changelog

## [0.16.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.15.0...launchdarkly-observability-android-0.16.0) (2025-11-22)


### Features

* XML Views Automasking options ([#299](https://github.com/launchdarkly/observability-sdk/issues/299)) ([c61a7be](https://github.com/launchdarkly/observability-sdk/commit/c61a7befc95ad7bef30c836b6464e17ca1f467a9))


### Bug Fixes

* missed imports ([#298](https://github.com/launchdarkly/observability-sdk/issues/298)) ([6e6c388](https://github.com/launchdarkly/observability-sdk/commit/6e6c3884af13c3677c4f9cbd629af8e99769dbe0))

## [0.15.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.14.1...launchdarkly-observability-android-0.15.0) (2025-11-21)


### Features

* Support ldMask() for Native and Compose views. ([#295](https://github.com/launchdarkly/observability-sdk/issues/295)) ([6233764](https://github.com/launchdarkly/observability-sdk/commit/6233764bbf5f9cb8385b37d1a25dd81d02a1cde9))

## [0.14.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.14.0...launchdarkly-observability-android-0.14.1) (2025-11-20)


### Bug Fixes

* tweaks Android InteractionDetector to delegate additional defaul… ([#294](https://github.com/launchdarkly/observability-sdk/issues/294)) ([aac1322](https://github.com/launchdarkly/observability-sdk/commit/aac13225d85a106070be66fbccbf72eaa6e24f98))

## [0.14.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.13.2...launchdarkly-observability-android-0.14.0) (2025-11-17)


### Features

* adds click and swipe interactions to session replay ([#292](https://github.com/launchdarkly/observability-sdk/issues/292)) ([4531005](https://github.com/launchdarkly/observability-sdk/commit/4531005fd1aea906754266cf350733d0b045233d))

## [0.13.2](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.13.1...launchdarkly-observability-android-0.13.2) (2025-11-04)


### Bug Fixes

* O11Y-725 - Return no-op span when not initialized ([#283](https://github.com/launchdarkly/observability-sdk/issues/283)) ([78ee2ec](https://github.com/launchdarkly/observability-sdk/commit/78ee2ec0db87d5326e703b6d5fbbeaaeee011ffe))

## [0.13.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.13.0...launchdarkly-observability-android-0.13.1) (2025-11-03)


### Bug Fixes

* O11Y-725 - Bug fix to avoid creating multiple OpenTelemetryRum instances when secondaryMobileKeys are set ([#281](https://github.com/launchdarkly/observability-sdk/issues/281)) ([09b2390](https://github.com/launchdarkly/observability-sdk/commit/09b2390c007bcf309e87a3366754878f63424a85))

## [0.13.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.12.0...launchdarkly-observability-android-0.13.0) (2025-10-31)


### Features

* Android observability session replay masking support ([#276](https://github.com/launchdarkly/observability-sdk/issues/276)) ([c34670b](https://github.com/launchdarkly/observability-sdk/commit/c34670b856a89176fc0e9b6eae0c32d349f2887d))
* O11Y-677 - Add session.id to all metrics ([#279](https://github.com/launchdarkly/observability-sdk/issues/279)) ([1927107](https://github.com/launchdarkly/observability-sdk/commit/19271077576523c470e11ad6f39c22d506f65768))

## [0.12.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.11.0...launchdarkly-observability-android-0.12.0) (2025-10-24)


### Features

* Android observability plugin session replay support ([#268](https://github.com/launchdarkly/observability-sdk/issues/268)) ([4ba5124](https://github.com/launchdarkly/observability-sdk/commit/4ba5124e59ebd5b46a55b124a749eeec715cff55))
* O11Y-601 - Add Android launch time instrumentation ([#274](https://github.com/launchdarkly/observability-sdk/issues/274)) ([38b4a84](https://github.com/launchdarkly/observability-sdk/commit/38b4a8475b51bd8686f5e1cc0e6c3d47e3ebc2e2))

## [0.11.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.10.0...launchdarkly-observability-android-0.11.0) (2025-10-10)


### Features

* **android:** Add conditional exporters for logs and traces ([#254](https://github.com/launchdarkly/observability-sdk/issues/254)) ([38f6c45](https://github.com/launchdarkly/observability-sdk/commit/38f6c45192a39d49aa629c2c5e24cd5310166003))

## [0.10.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.9.0...launchdarkly-observability-android-0.10.0) (2025-10-06)


### Features

* Cache OpenTelemetry metric instruments ([#256](https://github.com/launchdarkly/observability-sdk/issues/256)) ([db67867](https://github.com/launchdarkly/observability-sdk/commit/db67867acf4a956d4f4cdca0b329db9494601616))

## [0.9.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.8.0...launchdarkly-observability-android-0.9.0) (2025-10-01)


### Features

* O11Y-398 - Disable config options implemented ([#239](https://github.com/launchdarkly/observability-sdk/issues/239)) ([3548b42](https://github.com/launchdarkly/observability-sdk/commit/3548b42c6f4dbf3c710b508cb817b78cfc47448c))

## [0.8.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.7.0...launchdarkly-observability-android-0.8.0) (2025-09-26)


### Features

* O11Y-374 - Add identify span in TracingHook  ([#232](https://github.com/launchdarkly/observability-sdk/issues/232)) ([a895c97](https://github.com/launchdarkly/observability-sdk/commit/a895c9743ceb61fd045d0e561aa3b2c7b999067f))

## [0.7.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.6.0...launchdarkly-observability-android-0.7.0) (2025-09-23)


### Features

* Implement flush mechanism for observability data ([#229](https://github.com/launchdarkly/observability-sdk/issues/229)) ([4f266a6](https://github.com/launchdarkly/observability-sdk/commit/4f266a6eda9a8fa84780e2959d94bd4a937aa3d1))
* O11Y-413 - Add sampling config via web request ([#212](https://github.com/launchdarkly/observability-sdk/issues/212)) ([0a82cbf](https://github.com/launchdarkly/observability-sdk/commit/0a82cbf769f10292d3a9ccbc570d46e6d0558297))

## [0.6.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.5.0...launchdarkly-observability-android-0.6.0) (2025-08-28)


### Features

* O11Y-359 - Add custom sampling for OTLP logs and traces ([#189](https://github.com/launchdarkly/observability-sdk/issues/189)) ([3092c8f](https://github.com/launchdarkly/observability-sdk/commit/3092c8f3235a0b8f17210dba2469b7ffe8a6eca9))

## [0.5.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.4.0...launchdarkly-observability-android-0.5.0) (2025-08-26)


### Features

* adds otel-android HTTP instrumentation ([#195](https://github.com/launchdarkly/observability-sdk/issues/195)) ([e0baf35](https://github.com/launchdarkly/observability-sdk/commit/e0baf35b9e83e4539060520d5355d524de136944))

## [0.4.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.3.0...launchdarkly-observability-android-0.4.0) (2025-08-15)


### Features

* adds otel-android crash instrumentation ([#179](https://github.com/launchdarkly/observability-sdk/issues/179)) ([673b0d6](https://github.com/launchdarkly/observability-sdk/commit/673b0d63764f8a7419d0bf340f516103da913b3f))

## [0.3.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.2.0...launchdarkly-observability-android-0.3.0) (2025-08-13)


### Features

* adds configuration options to Android observability plugin ([#172](https://github.com/launchdarkly/observability-sdk/issues/172)) ([007e597](https://github.com/launchdarkly/observability-sdk/commit/007e597bc2c45a237160d977af8cd6c5c078b4fa))

## [0.2.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.2.0...launchdarkly-observability-android-0.2.0) (2025-08-11)


### Features

* adds startSpan to Android observability sdk and evaluation events on spans ([#141](https://github.com/launchdarkly/observability-sdk/issues/141)) ([58b74b7](https://github.com/launchdarkly/observability-sdk/commit/58b74b727f54f58e94439df77740c12b10518a9c))


### Miscellaneous Chores

* fixing android package group for publishing ([#160](https://github.com/launchdarkly/observability-sdk/issues/160)) ([f3f4fc7](https://github.com/launchdarkly/observability-sdk/commit/f3f4fc729c7c29cbc0a6084b0f1cf352d9c6da39))
* fixing android release ci caching ([#152](https://github.com/launchdarkly/observability-sdk/issues/152)) ([dd924eb](https://github.com/launchdarkly/observability-sdk/commit/dd924eb6330728c274d0bd99db6fcb0bc9b4ee7e))


### Continuous Integration

* adding group to observability-android gradle ([#156](https://github.com/launchdarkly/observability-sdk/issues/156)) ([c5a563c](https://github.com/launchdarkly/observability-sdk/commit/c5a563c9faf2048230b0682150ed1bca63506952))
