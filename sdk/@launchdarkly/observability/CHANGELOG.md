# @launchdarkly/observability

## [0.4.11](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.10...observability-0.4.11) (2025-12-18)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.25.0

## [0.4.10](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.9...observability-0.4.10) (2025-12-12)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.24.0

## [0.4.9](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.8...observability-0.4.9) (2025-11-12)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.23.0

## [0.4.8](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.7...observability-0.4.8) (2025-11-10)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.22.5

## [0.4.7](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.6...observability-0.4.7) (2025-10-30)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.22.4

## [0.4.6](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.5...observability-0.4.6) (2025-10-24)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.22.3

## [0.4.5](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.4...observability-0.4.5) (2025-10-06)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.22.2

## [0.4.4](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.3...observability-0.4.4) (2025-10-03)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.22.1

## [0.4.3](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.2...observability-0.4.3) (2025-10-01)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.22.0

## [0.4.2](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.1...observability-0.4.2) (2025-09-24)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.21.1

## [0.4.1](https://github.com/launchdarkly/observability-sdk/compare/observability-0.4.0...observability-0.4.1) (2025-09-09)


### Dependencies

* The following workspace dependencies were updated
  * dependencies
    * highlight.run bumped to 9.21.0

## [0.4.0](https://github.com/launchdarkly/observability-sdk/compare/observability-0.3.12...observability-0.4.0) (2025-08-29)


### Features

* Version Packages ([#136](https://github.com/launchdarkly/observability-sdk/issues/136)) ([c271dc8](https://github.com/launchdarkly/observability-sdk/commit/c271dc87e4aba78355ca54cc7af9200f63da26a8))

## 0.3.12

### Patch Changes

- Updated dependencies [7e8f9e5]
- Updated dependencies [7e8f9e5]
    - highlight.run@9.20.0

## 0.3.11

### Patch Changes

- 4ab5879: update o11y plugin docs to point to new launchdarkly pages and correctly reflect the SDK name

## 0.3.10

### Patch Changes

- Updated dependencies [1beae4d]
    - highlight.run@9.19.1

## 0.3.9

### Patch Changes

- Updated dependencies [d181f89]
    - highlight.run@9.19.0

## 0.3.8

### Patch Changes

- d0ab9e3: conditionally check for window api availability
- Updated dependencies [d0ab9e3]
    - highlight.run@9.18.23

## 0.3.7

### Patch Changes

- 54aab8b: delete sessionData\_ localstorage values to avoid overfilling quota
- Updated dependencies [54aab8b]
    - highlight.run@9.18.22

## 0.3.6

### Patch Changes

- 060dfe9: introduce LDObserve.stop api

## 0.3.5

### Patch Changes

- Updated dependencies [011032f]
    - highlight.run@9.18.21

## 0.3.4

### Patch Changes

- a151858: support delayed plugin initialization via manualStart setting and start API.
- Updated dependencies [a151858]
    - highlight.run@9.18.20

## 0.3.3

### Patch Changes

- Updated dependencies [9d1fa42]
    - highlight.run@9.18.19

## 0.3.2

### Patch Changes

- 3b7818c: update README EAP notice

## 0.3.1

### Patch Changes

- Updated dependencies [491a594]
    - highlight.run@9.18.18

## 0.3.0

### Minor Changes

- 3993134: refactor observability plugins to no longer require an observability project id

### Patch Changes

- 2dcbae1: correctly record console methods
- Updated dependencies [3993134]
- Updated dependencies [2dcbae1]
    - highlight.run@9.18.17

## 0.2.2

### Patch Changes

- 5fdfaba: 75983c01e66ee0f3fda29f7ab4a78254d11013a0
- 5fdfaba: update readmes to update early access preview progress
- 5fdfaba: add user agent and navigator language on trace attributes
- 5fdfaba: update flag span event names to follow feature_flag.evaluation convention
- 5fdfaba: add version reporting for @launchdarkly/observability
- Updated dependencies [5fdfaba]
- Updated dependencies [5fdfaba]
- Updated dependencies [5fdfaba]
    - highlight.run@9.18.16

## 0.2.1

### Patch Changes

- Updated dependencies [49b32d8]
    - highlight.run@9.18.15

## 0.2.0

### Minor Changes

- c901c22: move metrics listeners to Observability plugin from SessionReplay

### Patch Changes

- c901c22: fix ErrorListener incorrectly reporting stacktrace via trace event
- c901c22: fix document_load metric not forwarding to ldClient
- c901c22: allow reporting 0-value document_load durations
- Updated dependencies [c901c22]
- Updated dependencies [c901c22]
- Updated dependencies [c901c22]
    - highlight.run@9.18.14

## 0.1.17

### Patch Changes

- 3f513ca: remove verbosity of user instrumentation events by default.
  only reports click, input, and submit window events as spans unless `otel.eventNames` is provided.
- Updated dependencies [3f513ca]
    - highlight.run@9.18.13

## 0.1.16

### Patch Changes

- 575ac87: ensure omitting project id does not break sdk
- 575ac87: report all attributes of evaluation reason
- Updated dependencies [575ac87]
- Updated dependencies [575ac87]
    - highlight.run@9.18.12

## 0.1.15

### Patch Changes

- Updated dependencies [24ff099]
    - highlight.run@9.18.11

## 0.1.14

### Patch Changes

- b22881f: fix plugins not exporting types
- bcbb6f7: export plugin options correctly
- 188357c: fix session replay and observability plugin reporting to different sessions
- 18ff47a: fix span duplication happening due to an unnecessary export retry
- Updated dependencies [b22881f]
- Updated dependencies [bcbb6f7]
- Updated dependencies [188357c]
- Updated dependencies [18ff47a]
    - highlight.run@9.18.10

## 0.1.13

### Patch Changes

- d349bc2: wrap plugin initialization with try / catch to limit impact of internal errors
- Updated dependencies [0e87afd]
- Updated dependencies [d349bc2]
- Updated dependencies [0e87afd]
    - highlight.run@9.18.9

## 0.1.12

### Patch Changes

- 0ea7461: fix previous session not being resumed when tab is reloaded
- Updated dependencies [af513d3]
    - highlight.run@9.18.8

## 0.1.11

### Patch Changes

- Updated dependencies [57fa91d]
    - highlight.run@9.18.7

## 0.1.10

### Patch Changes

- Updated dependencies [36e2247]
- Updated dependencies [2c29bf4]
- Updated dependencies [a8bf1aa]
    - highlight.run@9.18.6

## 0.1.9

### Patch Changes

- Updated dependencies [bcd8ece]
    - highlight.run@9.18.5

## 0.1.8

### Patch Changes

- Updated dependencies [1ab53f8]
    - highlight.run@9.18.4

## 0.1.7

### Patch Changes

- Updated dependencies [bb75fea]
    - highlight.run@9.18.3

## 0.1.6

### Patch Changes

- Updated dependencies [027df7b]
    - highlight.run@9.18.2

## 0.1.5

### Patch Changes

- 24f38cf: update launchdarkly package reporting of attributes for application and environment

## 0.1.4

### Patch Changes

- 96d5818: change type exports to be compatible with moduleResolution node
- Updated dependencies [96d5818]
    - highlight.run@9.18.1

## 0.1.3

### Patch Changes

- 6e77363: include highlight.run as dependency for type resolution

## 0.1.2

### Patch Changes

- 1ea87fd: correctly publish dist directory

## 0.1.1

### Patch Changes

- bcac87b: release initial public version

## 0.1.0

### Minor Changes

- 26cc5f1: refactors highlight.run SDK into plugins consumed by new @launchdarkly packages
