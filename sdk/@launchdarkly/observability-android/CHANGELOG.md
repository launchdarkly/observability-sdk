# Changelog

## [0.43.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.42.0...launchdarkly-observability-android-0.43.0) (2026-05-01)


### Features

* **android:** add unmaskXMLViewIds to PrivacyProfile ([#522](https://github.com/launchdarkly/observability-sdk/issues/522)) ([5831209](https://github.com/launchdarkly/observability-sdk/commit/583120995dd64d4b85925f3f2bcdb111b1bb1478))
* **android:** update session replay masking precedence rules ([#518](https://github.com/launchdarkly/observability-sdk/issues/518)) ([4299d1e](https://github.com/launchdarkly/observability-sdk/commit/4299d1e65bdf7d3056b92631ba7eb1e36e3e96f5))

## [0.42.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.41.0...launchdarkly-observability-android-0.42.0) (2026-04-29)


### Features

* Android - downgrade Kotlin to 2.0.21, upgrade JVM to 11 ([#508](https://github.com/launchdarkly/observability-sdk/issues/508)) ([cc0fe6b](https://github.com/launchdarkly/observability-sdk/commit/cc0fe6bdce2088fc78683937efe70db976709b21))

## [0.41.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.40.0...launchdarkly-observability-android-0.41.0) (2026-04-24)


### Features

* **react-native:** Propagate LDContext to Android session replay via afterIdentify hook ([#495](https://github.com/launchdarkly/observability-sdk/issues/495)) ([8179a8e](https://github.com/launchdarkly/observability-sdk/commit/8179a8e5f632fdad86cc52f2acdf19bd51acff2a))

## [0.40.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.39.0...launchdarkly-observability-android-0.40.0) (2026-04-18)


### Features

* 50% decrease Nuget Android part in MAUI  ([#469](https://github.com/launchdarkly/observability-sdk/issues/469)) ([83d49af](https://github.com/launchdarkly/observability-sdk/commit/83d49afb536237a36b3e69e5de1641809b2a838b))

## [0.39.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.38.0...launchdarkly-observability-android-0.39.0) (2026-04-17)


### Features

* [SDK-2197] Add `LDReplay. registerActivity()` method for React Native. ([#472](https://github.com/launchdarkly/observability-sdk/issues/472)) ([f99b566](https://github.com/launchdarkly/observability-sdk/commit/f99b5660f68ac8ec1edf94f27e9eb29dea7c4d93))

## [0.38.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.37.0...launchdarkly-observability-android-0.38.0) (2026-04-13)


### Features

* Support 8 byte colors + non-linearity in hash ([#466](https://github.com/launchdarkly/observability-sdk/issues/466)) ([934d09a](https://github.com/launchdarkly/observability-sdk/commit/934d09a3041eb69945e823a2a12f13709d82d14a))

## [0.37.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.36.0...launchdarkly-observability-android-0.37.0) (2026-04-09)


### Features

* MAUI Network tracing 0.8.0 ([#463](https://github.com/launchdarkly/observability-sdk/issues/463)) ([0e2482b](https://github.com/launchdarkly/observability-sdk/commit/0e2482b61e59f4e935c053633974e95fef26fa85))

## [0.36.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.35.0...launchdarkly-observability-android-0.36.0) (2026-04-08)


### Features

* allow attach span context to logs ([#460](https://github.com/launchdarkly/observability-sdk/issues/460)) ([19ae071](https://github.com/launchdarkly/observability-sdk/commit/19ae071b6a89d9d47814ce4edea16b57f8cec3da))

## [0.35.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.34.1...launchdarkly-observability-android-0.35.0) (2026-04-03)


### Features

* Android Distro vs Sdk OpenTelemetry naming ([#458](https://github.com/launchdarkly/observability-sdk/issues/458)) ([edf968b](https://github.com/launchdarkly/observability-sdk/commit/edf968b52db17d40d9bca634810f5403e32b8830))

## [0.34.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.34.0...launchdarkly-observability-android-0.34.1) (2026-03-28)


### Bug Fixes

* move Otel testing library out production runtime ([#446](https://github.com/launchdarkly/observability-sdk/issues/446)) ([8a8ef34](https://github.com/launchdarkly/observability-sdk/commit/8a8ef344c15bedfa562d28dbb4904ceb0cdf34a3))

## [0.34.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.33.0...launchdarkly-observability-android-0.34.0) (2026-03-27)


### Features

* o3 level optimization for image diffing ([#443](https://github.com/launchdarkly/observability-sdk/issues/443)) ([9045142](https://github.com/launchdarkly/observability-sdk/commit/9045142c7455b3f12574a1aacd21e582c89d8680))


### Bug Fixes

* Build warnings and compatibility issues across Android and MAUI (.NET) SDKs. ([#439](https://github.com/launchdarkly/observability-sdk/issues/439)) ([47c4640](https://github.com/launchdarkly/observability-sdk/commit/47c4640612bb0d769f6e1cde6c98e7c7683520d9))

## [0.33.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.32.0...launchdarkly-observability-android-0.33.0) (2026-03-27)


### Features

* Support Traces, Logs, Metrics in mobile dotnet 0.5.0 ([#428](https://github.com/launchdarkly/observability-sdk/issues/428)) ([8e9483a](https://github.com/launchdarkly/observability-sdk/commit/8e9483aadf13954e843b8ac8b8574a46456a4694))

## [0.32.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.31.0...launchdarkly-observability-android-0.32.0) (2026-03-26)


### Features

* **@launchdarkly/session-replay-react-native:** use cocoapods for native session replay ([#434](https://github.com/launchdarkly/observability-sdk/issues/434)) ([41988e1](https://github.com/launchdarkly/observability-sdk/commit/41988e196b02901964efdad0e25f892d5ba55fc4))
* MAUI integration hooks and refactor for 0.4.1 nuget ([#425](https://github.com/launchdarkly/observability-sdk/issues/425)) ([33f6d6c](https://github.com/launchdarkly/observability-sdk/commit/33f6d6c29639520e6c8b303047f4659d1016dc3d))

## [0.31.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.30.0...launchdarkly-observability-android-0.31.0) (2026-03-09)


### Features

* Use C and NEON for hashing (Optimization) 100x on Pixel 8 ([#415](https://github.com/launchdarkly/observability-sdk/issues/415)) ([e7c78ab](https://github.com/launchdarkly/observability-sdk/commit/e7c78abda3954a1a2d32b5a85f57eab531eecaae))

## [0.30.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.29.0...launchdarkly-observability-android-0.30.0) (2026-03-07)


### Features

* Android SR use Jpeg 0.3 quality ([#417](https://github.com/launchdarkly/observability-sdk/issues/417)) ([ad6d0aa](https://github.com/launchdarkly/observability-sdk/commit/ad6d0aabbf35ac81e6ca36abe9fd831f92545236))

## [0.29.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.28.0...launchdarkly-observability-android-0.29.0) (2026-03-05)


### Features

* Android Observability hook  proxy for MAUI ([#409](https://github.com/launchdarkly/observability-sdk/issues/409)) ([8d610bd](https://github.com/launchdarkly/observability-sdk/commit/8d610bdd700fd2878e58095e5fd8b98ef2765df6))


### Bug Fixes

* reset nodeIds during fullsnapshot ([#412](https://github.com/launchdarkly/observability-sdk/issues/412)) ([d1eb13d](https://github.com/launchdarkly/observability-sdk/commit/d1eb13db8907f17406f45a2a7e3b76aec3a7c1ec))

## [0.28.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.27.0...launchdarkly-observability-android-0.28.0) (2026-03-02)


### Features

* Make Android SDK35 compilable ([#405](https://github.com/launchdarkly/observability-sdk/issues/405)) ([278880d](https://github.com/launchdarkly/observability-sdk/commit/278880dae408afe304e35c00ebe989e466f509eb))

## [0.27.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.26.1...launchdarkly-observability-android-0.27.0) (2026-02-28)


### Features

* Android Incremental Image Diff compression ([#390](https://github.com/launchdarkly/observability-sdk/issues/390)) ([5ff93f6](https://github.com/launchdarkly/observability-sdk/commit/5ff93f61e078b27ca7d15a49e35ac98076d684cc))
* Optional Jet Compose ([#402](https://github.com/launchdarkly/observability-sdk/issues/402)) ([8f3a671](https://github.com/launchdarkly/observability-sdk/commit/8f3a671a7853353b3e255882b66c6cb04506dcaa))

## [0.26.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.26.0...launchdarkly-observability-android-0.26.1) (2026-02-24)


### Bug Fixes

* Fix event sorting in session replay export ([#392](https://github.com/launchdarkly/observability-sdk/issues/392)) ([5e11cf7](https://github.com/launchdarkly/observability-sdk/commit/5e11cf71d60a7d7a6131a2d8fb5fb1f70c6916bc))
* Touch move event buffering using wrong clock and mismatched constants ([#391](https://github.com/launchdarkly/observability-sdk/issues/391)) ([0a6582c](https://github.com/launchdarkly/observability-sdk/commit/0a6582c2ba445cd391a03d3aecfa723887f636da))

## [0.26.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.25.2...launchdarkly-observability-android-0.26.0) (2026-02-17)


### Features

* Android pixel density settings. ([#373](https://github.com/launchdarkly/observability-sdk/issues/373)) ([58a3a7b](https://github.com/launchdarkly/observability-sdk/commit/58a3a7b2555a0d4b898c8d178801fc2988783765))

## [0.25.2](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.25.1...launchdarkly-observability-android-0.25.2) (2026-02-17)


### Bug Fixes

* Android SR, remove visible scrollbars ([#371](https://github.com/launchdarkly/observability-sdk/issues/371)) ([c21ecd4](https://github.com/launchdarkly/observability-sdk/commit/c21ecd44c1dcb94a11bd717688b8e76f1f4aff16))

## [0.25.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.25.0...launchdarkly-observability-android-0.25.1) (2026-02-12)


### Bug Fixes

* release package by trigger new build ([#364](https://github.com/launchdarkly/observability-sdk/issues/364)) ([7eaf5cd](https://github.com/launchdarkly/observability-sdk/commit/7eaf5cd576b9aa9003e6b4a4372c61bc3d390ff1))
* Trigger release / Code comments change ([#363](https://github.com/launchdarkly/observability-sdk/issues/363)) ([06b34da](https://github.com/launchdarkly/observability-sdk/commit/06b34dae442a0f4a1c7b18a0bcda89d561f445db))

## [0.25.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.24.0...launchdarkly-observability-android-0.25.0) (2026-01-28)


### Features

* O11Y-969 - Add runtime control for Session Replay capture  ([#355](https://github.com/launchdarkly/observability-sdk/issues/355)) ([1d4398e](https://github.com/launchdarkly/observability-sdk/commit/1d4398edbf9016cecd910da144da383b83e53197))

## [0.24.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.23.1...launchdarkly-observability-android-0.24.0) (2026-01-23)


### Features

* O11Y-920 - Add masking support for WebViews ([#352](https://github.com/launchdarkly/observability-sdk/issues/352)) ([b1b46d3](https://github.com/launchdarkly/observability-sdk/commit/b1b46d37017a551cf7f3f14d1b8b78eedf3d3d6b))

## [0.23.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.23.0...launchdarkly-observability-android-0.23.1) (2026-01-16)


### Bug Fixes

* flag eval snap name ([#345](https://github.com/launchdarkly/observability-sdk/issues/345)) ([f10980e](https://github.com/launchdarkly/observability-sdk/commit/f10980eb73924935e8fb363c87a4a6c8e7956012))

## [0.23.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.22.0...launchdarkly-observability-android-0.23.0) (2026-01-09)


### Features

* Android double masking with frame drop ([#342](https://github.com/launchdarkly/observability-sdk/issues/342)) ([803fb83](https://github.com/launchdarkly/observability-sdk/commit/803fb83a906d0b5d2e0d7f26d7746d25adb1b37f))

## [0.22.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.21.0...launchdarkly-observability-android-0.22.0) (2026-01-07)


### Features

* Added privacy options: maskViews, maskXMLViewIds, maskImageViews ([#339](https://github.com/launchdarkly/observability-sdk/issues/339)) ([1c57dc0](https://github.com/launchdarkly/observability-sdk/commit/1c57dc00fa1248a8005df48de520aa7416d14225))

## [0.21.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.20.0...launchdarkly-observability-android-0.21.0) (2025-12-18)


### Features

* Pause and resume replay capture on app background/foreground ([#329](https://github.com/launchdarkly/observability-sdk/issues/329)) ([771ce51](https://github.com/launchdarkly/observability-sdk/commit/771ce5121d6849705b9e88bc73dcb30b6879032e))


### Bug Fixes

* Fix compose coordinate offset.  ([#331](https://github.com/launchdarkly/observability-sdk/issues/331)) ([7fbdc4c](https://github.com/launchdarkly/observability-sdk/commit/7fbdc4c2096f4785b80333512d21d27b556317cd))

## [0.20.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.19.1...launchdarkly-observability-android-0.20.0) (2025-12-18)


### Features

* Android SR Identify support  ([#330](https://github.com/launchdarkly/observability-sdk/issues/330)) ([a421812](https://github.com/launchdarkly/observability-sdk/commit/a4218120ceb3b423e56118fcde0e72beffec2b5c))
* Graphql client memory optimization ([#325](https://github.com/launchdarkly/observability-sdk/issues/325)) ([f199e2d](https://github.com/launchdarkly/observability-sdk/commit/f199e2d2b60261f0d905baa3b3e79f3b3d08e8ca))
* Gzip compression for Graphql request body ([#328](https://github.com/launchdarkly/observability-sdk/issues/328)) ([d862a15](https://github.com/launchdarkly/observability-sdk/commit/d862a15d8c5611f3a9d836f444d55ed69eee2bb4))
* Limit accumulating canvas buffer ([#322](https://github.com/launchdarkly/observability-sdk/issues/322)) ([72f2592](https://github.com/launchdarkly/observability-sdk/commit/72f2592df37a9160e54c775d5be72cfb6312bd21))

## [0.19.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.19.0...launchdarkly-observability-android-0.19.1) (2025-12-11)


### Bug Fixes

* Android - Remove Disk Buffering ([#315](https://github.com/launchdarkly/observability-sdk/issues/315)) ([38b1803](https://github.com/launchdarkly/observability-sdk/commit/38b18037842e0bf1cb8f89c424d63162ecd53bfe))

## [0.19.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.18.0...launchdarkly-observability-android-0.19.0) (2025-12-04)


### Features

* take transformed coordinates, which are more precise in animation  ([#309](https://github.com/launchdarkly/observability-sdk/issues/309)) ([5d669d4](https://github.com/launchdarkly/observability-sdk/commit/5d669d49a7d412b4edce8e5f5bdc7728243bd2c3))

## [0.18.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.17.0...launchdarkly-observability-android-0.18.0) (2025-12-04)


### Features

* Android SR Do not send duplicate screens ([#304](https://github.com/launchdarkly/observability-sdk/issues/304)) ([f3369bc](https://github.com/launchdarkly/observability-sdk/commit/f3369bc87f7e1293c8bdabf592693b8365600312))
* recursive mask collection ([#308](https://github.com/launchdarkly/observability-sdk/issues/308)) ([ee9f061](https://github.com/launchdarkly/observability-sdk/commit/ee9f0610d199378b368cd5a91aa259254b27511a))
* support non-standard windows added by WindowManager ([#306](https://github.com/launchdarkly/observability-sdk/issues/306)) ([199374a](https://github.com/launchdarkly/observability-sdk/commit/199374a30c67da7d8151cbb65c8eb1a50545006c))

## [0.17.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-android-0.16.0...launchdarkly-observability-android-0.17.0) (2025-11-26)


### Features

* Android Dialog Capture ([#302](https://github.com/launchdarkly/observability-sdk/issues/302)) ([11b642f](https://github.com/launchdarkly/observability-sdk/commit/11b642fbfae70fd39d57efadfbdc285e88b73477))

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
