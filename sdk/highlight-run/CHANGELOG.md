# highlight.run

## [9.21.0](https://github.com/launchdarkly/observability-sdk/compare/highlight.run-9.20.0...highlight.run-9.21.0) (2025-09-09)

### Bug Fixes

* Fix an issue where metrics could have NaN or infinite values. ([#220](https://github.com/launchdarkly/observability-sdk/issues/220)) ([1e4cc34](https://github.com/launchdarkly/observability-sdk/commit/1e4cc342ecd0636dff28448fc932f242ab06b46b))
* Remove debug logs for export failures. ([#218](https://github.com/launchdarkly/observability-sdk/issues/218)) ([872c1f7](https://github.com/launchdarkly/observability-sdk/commit/872c1f74a43e0ddf5a1e9e43a9ef93d5e2946ff4))

## 9.20.0

### Minor Changes

- 7e8f9e5: Add support for specifying a contextFriendlyName function.
- 7e8f9e5: Fix generation of canonical key.

## 9.19.1

### Patch Changes

- 1beae4d: add sampling support to react native o11y plugin

## 9.19.0

### Minor Changes

- d181f89: Define session based on a sessionKey

## 9.18.23

### Patch Changes

- d0ab9e3: conditionally check for window api availability

## 9.18.22

### Patch Changes

- 54aab8b: delete sessionData\_ localstorage values to avoid overfilling quota

## 9.18.21

### Patch Changes

- 011032f: Add upper version constraint for otel packages.

## 9.18.20

### Patch Changes

- a151858: support delayed plugin initialization via manualStart setting and start API.

## 9.18.19

### Patch Changes

- 9d1fa42: report member.email from context object

## 9.18.18

### Patch Changes

- 491a594: Add IDs to error instances.

## 9.18.17

### Patch Changes

- 3993134: refactor observability plugins to no longer require an observability project id
- 2dcbae1: provide LDContext user.memberEmail to H.identify when available

## 9.18.16

### Patch Changes

- 5fdfaba: add user agent and navigator language on trace attributes
- 5fdfaba: update flag span event names to follow feature_flag.evaluation convention
- 5fdfaba: fix internal version reporting

## 9.18.15

### Patch Changes

- 49b32d8: Improve logging for persistent storage issues.

## 9.18.14

### Patch Changes

- c901c22: fix ErrorListener incorrectly reporting stacktrace via trace event
- c901c22: fix document_load metric not forwarding to ldClient
- c901c22: allow reporting 0-value document_load durations

## 9.18.13

### Patch Changes

- 3f513ca: remove verbosity of user instrumentation events by default.
  only reports click, input, and submit window events as spans unless `otel.eventNames` is provided.

## 9.18.12

### Patch Changes

- 575ac87: ensure omitting project id does not break sdk
- 575ac87: report all attributes of evaluation reason

## 9.18.11

### Patch Changes

- 24ff099: Only add context data for successful identify operations.

## 9.18.10

### Patch Changes

- b22881f: fix plugins not exporting types
- bcbb6f7: export plugin options correctly
- 188357c: fix session replay and observability plugin reporting to different sessions
- 18ff47a: fix span duplication happening due to an unnecessary export retry

## 9.18.9

### Patch Changes

- 0e87afd: improve opentelemetry exporter retry logic to delay retries
- d349bc2: wrap plugin initialization with try / catch to limit impact of internal errors
- 0e87afd: improve internal reporting of warnings and errors to LDObserve

## 9.18.8

### Patch Changes

- af513d3: Use verbose organization ID for accessing sampling configuration.

## 9.18.7

### Patch Changes

- 57fa91d: add new evaluation attributes reported by @launchdarkly/observability

## 9.18.6

### Patch Changes

- 36e2247: correctly provide session url for launchdarkly frontend
- 2c29bf4: update launchdarkly integration to report sessionID to track forwarding
- a8bf1aa: correct launchdarkly product api types

## 9.18.5

### Patch Changes

- bcd8ece: exclude launchdarkly events and evals from being recorded by the observability sdk

## 9.18.4

### Patch Changes

- 1ab53f8: Add support for client-side sampling.

## 9.18.3

### Patch Changes

- bb75fea: fix highlight.run unpkg build

## 9.18.2

### Patch Changes

- 027df7b: gate plugin startup to prevent failing in non-browser envs

## 9.18.1

### Patch Changes

- 96d5818: change type exports to be compatible with moduleResolution node

## 9.18.0

### Minor Changes

- 26cc5f1: refactors highlight.run SDK into plugins consumed by new @launchdarkly packages

## 9.17.1

### Patch Changes

- 63ba039: update exporter to avoid duplicate beacon ping requests

## 9.17.0

### Minor Changes

- ce2407a: Set up new data emission in highlight SDK from launchdarkly SDK.
    - adds H.log function to report custom logs from the highlight SDK
    - use traces instead of track events from LD track calls
    - record logs for identify integration from LD identify calls

## 9.16.2

### Patch Changes

- 7d1a4af: move bundled dependencies into dev dependencies

## 9.16.1

### Patch Changes

- 08290c0: reports web vitals metrics, session initialization, document_load, and track events correctly to LD integration

## 9.16.0

### Minor Changes

- fd77bfb: introduce launchdarkly sdk integration for event forwarding

### Patch Changes

- 9cedbe7: improve LD sdk integration to report context

## 9.15.0

### Minor Changes

- 06d109a: turn off session cookie storage by default

### Patch Changes

- 0acb4c2: fix local sendmode not incrementing payload id

## 9.14.0

### Minor Changes

- 5a55386: update rrweb recording engine

### Patch Changes

- 6b8fa6f: remove type assertions around tracer on shutdown

## 9.13.0

### Minor Changes

- 70cd598: Add more granualar otel instrumentation configuration

## 9.12.0

### Minor Changes

- 6a3b836: Add option to disable frontend tracing

## 9.11.1

### Patch Changes

- 2b8cba8: ensure otel instrumentation does not interfere with other otel setups
- baed388: avoid emitting relativeTimestamp metrics

## 9.11.0

### Minor Changes

- 941ac5e: support inlining any video tags by snapshotting as a canvas via inlineVideos setting

## 9.10.0

### Minor Changes

- b4f7173: turn off firstload network recording in favor of opentelemetry instrumentation

### Patch Changes

- b4f7173: check trace header injection separately from body capture check

## 9.9.1

### Patch Changes

- 91f82fc: record highlight session id on client-side metrics

## 9.9.0

### Minor Changes

- ab181de: add a network performance listener to report network connection properties

## 9.8.0

### Minor Changes

- 0fa5585: introduce otlp native metrics export

## 9.7.4

### Patch Changes

- 408abc7: reduce noisy ErrorStackParser.parse failures when failing to parse provided error

## 9.7.3

### Patch Changes

- d0e47ed: fix capture of window user interaction events.
  the window.addEventListener monkeypatch would
  break libraries relying on the API because
  the debounce logic would incorrectly call the
  listener on the debounce condition.
  adds additional events to the instrumentation.
- f396ee3: tune opentelemetry traces export from highlight.run

## 9.7.2

### Patch Changes

- 02fad3d: add url.\* attributes to fetch and xhr traces
- da5662e: avoid setting up promise monkeypatch by default to avoid breaking libraries that depend on the native promise implementation

## 9.7.1

### Patch Changes

- 847fdc0: revert single style sheet serialization due to performance regression

## 9.7.0

### Minor Changes

- ecde630: enable browser OTeL by default

## 9.5.3

### Patch Changes

- 2d95aba: Only send visited-url fields through the session events api

## 9.5.2

### Patch Changes

- df0b226: respect traceOrigins setting for context propagation

## 9.5.1

### Patch Changes

- 5194753: fix `H.getSession*` methods using stale session ID

## 9.5.0

### Minor Changes

- d94533a: update rrweb to use postcss css parser

## 9.4.4

### Patch Changes

- f43d3b4: fix full snapshot error when failing to starting rrweb recording
- 72ec866: fix recording bodies of otel requests
- f43d3b4: improve canvas serialization performance and support shadow dom canvases
- f43d3b4: fix rrweb replay breaking on invalid inlined css

## 9.4.3

### Patch Changes

- 54557e9: re-release 9.4.2 which included incorrect bundle built from 9.3.4

## 9.4.2

### Patch Changes

- 5213ca3: ensure callback value is returned when not initialized

## 9.4.1

### Patch Changes

- a95d52b: record pri.highlight.io requests
- f432e66: block tracing via x-highlight-request header via urlblocklist

## 9.4.0

### Minor Changes

- 815faa8: fix rrweb postcss replay and live mode

### Patch Changes

- 815faa8: fix live mode breaking due to ischeckout of full snapshot

## 9.3.4

### Patch Changes

- 02f67d0: Fix type error reporting screen orientation

## 9.3.3

### Patch Changes

- 5cc0afd: correctly report clickTextContent as timeline events

## 9.3.2

### Patch Changes

- be38f68: make client kill switch less likely to trigger by requiring multiple failures
- 2339697: update opentelemetry dependencies
- bbbaeb1: ensure duplicate tab functionality does not break x-highlight-request header
  corrects issue introduced in 9.3.0 with the x-highlight-request missing the session id

## 9.3.1

### Patch Changes

- 262a07314: revert postcss changes to css parsing in rrweb
  https://github.com/rrweb-io/rrweb/pull/1458 introduced
  a new CSS parser which causes issues with certain large CSS files

## 9.3.0

### Minor Changes

- 0a8a9ffdc: add cookie session persistence

### Patch Changes

- d2e00028a: correctly set x-highlight-request on outgoing fetch/xhr requests with duplicate tab recording.
  the sessionID in the x-highlight-request would not be set correctly after recent changes
  corrected the multi-tab behavior to clear the local storage sessionID value to ensure
  new tabs started unique sessions. corrects bug affecting >=9.1.5
- 0a8a9ffdc: update otel webjs network span naming

## 9.2.2

### Patch Changes

- f7fb74a44: add XHR request fallback is sendBeacon fails

## 9.2.1

### Patch Changes

- 0067ea6b5: use trace ID as request ID in network listeners

## 9.2.0

### Minor Changes

- d67bd4425: stop recording if pushpayload cannot keep up with uploading data

## 9.1.5

### Patch Changes

- 5b0b5a503: update rrweb to ^2.0.0-alpha.17
  ensure multiple tabs report data to distinct sessions

## 9.1.4

### Patch Changes

- 3e99f48ca: fix otel webjs event monkeypatch breaking mouse event listeners

## 9.1.3

### Patch Changes

- efdf6b66a: fix highlight.run getSessionUrl only returning session ID

## 9.1.2

### Patch Changes

- 50dba067f: fix otel webjs startSpan crashing when otel code has not loaded

## 9.1.1

### Patch Changes

- 05fbf19aa: report performance resource timings in nanoseconds
- 24c5b00b6: fix span renaming of GraphQL requests

## 9.1.0

### Minor Changes

- 463e99106: add browser OTEL tracing
- 8fd0e8f92: add `startSpan` and `startManualSpan` methods

## 9.0.5

### Patch Changes

- e239b1a02: fix cross-origin iframe recording

## 9.0.4

### Patch Changes

- 0a245b208: default to jpeg canvas recording when webp not supported

## 9.0.3

### Patch Changes

- 50c74161e: add screen measurements to session viewport data

## 9.0.2

### Patch Changes

- fd011e719: ensure getSessionURL correctly returns session url

## 9.0.1

### Patch Changes

- 2ced0e269: fix inlineImages video recording showing incorrect solution and breaking videos on load

## 9.0.0

### Major Changes

- e7eb5f581: updates rrweb to 2.0.15 with LWC support

## 8.13.0

### Minor Changes

- 8905154ff: make browser sdk more robust to avoid broken sessions

## 8.12.4

### Patch Changes

- 2a90db809: improve dev reliability of browser fetch patch
- 2a90db809: record more attributes as part of captured frontend network requests

## 8.12.3

### Patch Changes

- 2ea916328: Fix duplicate network requests when performance.clearResourceTimings has been monkeypatched by external libraries
- 11fe2921b: export highlight state on sdk

## 8.12.2

### Patch Changes

- 8899ee039: report additional metadata on error boundary exceptions

## 8.12.1

### Patch Changes

- 926c3a1d7: fix rrweb merge conflict breaking canvas recording

## 8.12.0

### Minor Changes

- b27a0bcd2: update rrweb version to 729361e

## 8.11.1

### Patch Changes

- 4ecafffe6: fix xhr monkeypatch breaking for URL objects

## 8.11.0

### Minor Changes

- 4574c8dfa: lazy load client bundle from node modules to avoid ad blockers

## 8.10.1

### Patch Changes

- bd410081e: switch replay to using highlight backend for font cors proxying

## 8.10.0

### Minor Changes

- 112fa2ced: Fix bug with recording traces.

## 8.9.1

### Patch Changes

- 3528f2de7: support sonner by disabling promise monkeypatching

## 8.9.0

### Minor Changes

- f45323273: Update websocket events to use absolute timestamps. Remove relative timestamps from all requests.

## 8.8.0

### Minor Changes

- 23a01e3d6: update rrweb to pr-1352

### Patch Changes

- 6a8151dc3: fix console log serialization

## 8.7.1

### Patch Changes

- 31486ce41: Deep stringify network request and response bodies

## 8.7.0

### Minor Changes

- e2483b6c3: inline stylesheets by default

## 8.6.0

### Minor Changes

- 58ad9560f: support custom serialization for log attributes to display cleaner message bodies

### Patch Changes

- ed3ff4752: filter network request recording for highlight.io traffic
- 9796ef086: report browser performance events as metrics

## 8.5.0

### Minor Changes

- 102710b30: Make improvements to `requestResponseSanitizer` method to pass function in a body converted to json instead of a string.

## 8.4.1

### Patch Changes

- 52b260556: fix innerText attribute change obfuscation

## 8.4.0

### Minor Changes

- 7a3e3d077: optimize data transfer from browser sdk by compressing uploads
  changes data export to https://pub.highlight.io which may require CSP setting changes

### Patch Changes

- 59952b854: update highlight.run client import for nodenext resolution
- 59952b854: fix promise patch for angular.js

## 8.3.2

### Patch Changes

- a07cdf584: correctly record stack trace for async promise rejections
- 4493988b0: support sending structured attributes in browser console logs

## 8.3.1

### Patch Changes

- 85ea62d0c: Add environment to backend error types.

## 8.3.0

### Minor Changes

- 84110aca1: Update default privacy mode to obfuscate all inputs by default. Allow user to override ofuscation with data-hl-record attribute. Fix regex expressions for telephone numbers and addresses.

### Patch Changes

- c1773fa66: ensure cross origin iframe recording works even if the iframe reloads

## 8.2.3

### Patch Changes

- f966390c1: ensure compatibility for JS SDKs in ES and CJS environments

## 8.2.2

### Patch Changes

- b6172b0da: support bypassing sessionStorage entirely with the storageMode option and provide a globalStorage fallback

## 8.2.1

### Patch Changes

- 7c20f8c44: revert "ensure highlight.run script tag is only added once (#7005)"

## 8.2.0

### Minor Changes

- 8142463b5: ensure highlight script tag is only inserted once to optimize for browser performance by reducing unused javascript in next.js environments

## 8.1.0

### Minor Changes

- b03039b6b: Adds support for `requestResponseSanitizer` to allow users to modify data from the request/response headers and body, as well as prevent the entire request/response from being logged.

### Patch Changes

- 7b931c336: ensure canvas recording works with auto snapshotting by default if no samplingStrategy is set
- be3f51f45: Adding .js file extensions to support NodeNext module resolution in TypeScript

## 8.0.1

### Patch Changes

- e7fa17ac7: make H.init({forceNew: true}) reset the user identifier of the new session

## 8.0.0

### Major Changes

- 4f4e5aa4f: Switches privacy settings from `enableStrictPrivacy` to `privacySetting`, which will have a `'default'` mode that uses common regex expressions and input names to obfuscate personally identifiable information. Strict privacy mode is unchanged, and can be used by setting `privacySetting` to `'strict'`. No obfuscation can still be used by setting `privacySetting` to `'none'`.

## 7.6.0

### Minor Changes

- e264f6a61: ignore non-actionable / internal errors in client-side error handling

## 4.6.0

### Minor Changes

- fix workspace:\* dependencies

## 5.0.0

### Major Changes

- Pins highlight.run dynamic client version to the highlight.run package version.

### Upgrading from 4.x or older

- We've migrated our lazy-loaded bundled to be served from static.highlight.io instead of static.highlight.run. As a
  result, if you use custom, change references form static.highlight.run to static.highlight.io.
- There are no breaking API / package behavior changes with this release.

## 5.0.1

### Patch Changes

- Ensures that a tab reload that resumes a previous old session (older than 4 hours) starts a new session rather than
  adding a set of data for the previous one.

## 5.1.0

### Minor Changes

- Improves canvas recording efficiency.
- Improves accuracy of recorded iframes.
- Improves recording of scrolling with custom CSS.
- Improves recording of Shadow DOM elements.
- Smoothens replay mouse animation.

## 5.1.1

### Patch Changes

Ensures H.stop() stops recording and that visibility events do not restart recording.

- Simplify CSP requirements by proxying web-vitals script.

## 5.1.2

### Patch Changes

- Fix an issue that prevented recording from starting.

## 5.1.3

### Patch Changes

- Separate the client web worker to make content security policy more strict.

## 5.1.4

### Patch Changes

- Fix an issue with tab visibility switches breaking some recordings.

## 5.1.5

### Patch Changes

- Fix typescript definitions in published `highlight.run` npm package.

## 5.1.6

### Patch Changes

- Remove randomized URL param from Highlight client script to allow browser caching by client version.

## 5.1.7

### Patch Changes

- Ensure `<video>` and `<audio>` elements are obfuscated correctly in strict privacy mode or
  with `highlight-mask` / `highlight-block`.
- Fix a condition where `enableStrictPrivacy` with `highlight-mask` on a `<div>` would cause child elements to not be
  recorded.

## 5.1.8

### Patch Changes

- Resolves an issue with recording events that have listeners calling preventDefault().

## 5.2.0

### Minor Changes

- Adds support for cross-origin iframe recording.

## 5.2.1

### Patch Changes

- Adds a list of non-retryable errors to prevent the client from unnecessary retries

## 5.2.2

### Patch Changes

- Fixes issues in Shadow DOM recording that would omit sections of the DOM.

## 5.2.3

### Patch Changes

- Fixes Highlight integration
  with [Segment V2 (aka @segment/analytics-next)](https://www.npmjs.com/package/@segment/analytics-next).
- Changes iframe recording behavior for cross-origin iframes to ensure `src` is dropped as the `src` cannot be replayed.

## 5.3.3

### Minor Changes

- Fixes cross-origin iframe bugs.
- Add ability to opt out of client integrations.

### Patch Changes

- Updates rollup dependency.
- Defaults to inlining stylesheets.
- Replaces fingerprint with generated client id.
- Enables console and error recording on localhost.

## 5.4.0

### Minor Changes

- Adds `recordCrossOriginIframe` setting to opt-in enable cross-origin iframe recording.

## 5.4.1

### Patch Changes

- Ensure integrations are not initialized when `disabled: true`.

## 5.4.2

### Patch Changes

- Adds an opt-out `reportConsoleErrors` boolean setting to `H.init` that allows disabling reporting console logs as errors.
- Ensures `console.error(...)` calls are reported as part of highlight frontend sessions in all cases.

## 6.0.0

### Major Changes

- Switches `reportConsoleErrors` to be disabled by default. With the setting disabled, `console.error(...)` calls will only be reported as error logs.
- Adds a `disableSessionRecording` setting that allows using the javascript sdk for error/logs recording without capturing session replays.
- Updates rrweb dependency.

## 6.0.1

### Patch Changes

- Fixes `H.track` reporting to ensure events are recorded as part of the session timeline indicators.

## 6.0.2

### Patch Changes

- Fixes typescript definitions for `highlight.run` which referenced an internal unpublished package.

## 6.0.3

### Patch Changes

- Packages the web-vitals library as part of the highlight.io client bundle.

## 6.2.0

### Minor Changes

- Supports recording inlined `<video>` elements such as webcams or `src="blob://...`.
- Limits the size of network request bodies recorded to prevent replay-time crashes.

## 6.3.0

### Minor Changes

- Support the option to redact specific request/response body keys while recording all others.

## 6.4.0

### Minor Changes

- Moves bundling from rollup to vite.

## 6.4.1

### Patch Changes

- Switch to umd default output.

## 6.4.3

### Patch Changes

- Fixes to umd format

## 6.5.0

### Minor Changes

- Adds an `H.start({forceNew: true})` option that allows forcing the start of a new session recording.

## 6.5.1

### Patch Changes

- Turn off client sourcemaps as they cause issues with next.js frontends.

## 6.5.2

### Patch Changes

- Target ES6 for library build compatibility.

## 6.5.3

### Patch Changes

- The Highlight `window.fetch` proxy was only forwarding headers from `RequestInit`. It now forwards headers from `RequestInfo` as well.
- Target ES6 for library build compatability.

## 7.0.0

### Breaking Changes

- Removed the `feedbackWidget` option.

## 7.1.0

### Minor Changes

- Improves the experience of configuring cross-origin `<iframe>` recording.

## 7.1.1

### Patch Changes

- Extends the length of recorded sessions for a given project.

## 7.1.2

### Minor Changes

- Avoid initializing highlight fetch monkeypatch more than once.

## 7.2.0

### Minor Changes

- Capture unhandled promise exceptions in highlight errors.

## 7.3.0

### Minor Changes

- Update format of data sent in for WebSocket events

## 7.3.1

### Patch Changes

- Increase data transmission retry delays.

## 7.3.2

### Patch Changes

- Ensure compatibility with native `window.Highlight` [class](https://developer.mozilla.org/en-US/docs/Web/API/Highlight).

## 7.3.3

### Patch Changes

- Ensure `console.error` caught stack traces are not missing the top frame.

## 7.3.4

### Patch Changes

- Add easier testing of local `@highlight-run/client` and `highlight.run` scripts.
- Look for `window.HighlightIO` instead of `window.Highlight` when waiting for client script to load.

## 7.3.5

### Patch Changes

- Remove any properties that throw a `structuredClone` error in `addProperties` before calling `postMessage`

## 7.3.6

### Patch Changes

- Track identify metadata in the mixpanel integration as a tracked event.

## 7.3.7

Reserved for the Boeing 737

## 7.3.8

### Patch Changes

- Fix `window.Promise` monkeypatch to work in Next.js frontends.

## 7.3.9

### Patch Changes

- Fix recording of WebGL2 `<canvas>` elements that leverage `preserveDrawingBuffer: false`

## 7.3.10

### Patch Changes

- Fix error capture of `new Error()` objects.

## 7.3.11

### Patch Changes

- Improve `canvasInitialSnapshotDelay` logic for `<canvas>` recording to delay per-canvas.

## 7.3.12

### Patch Changes

- Update naming of exports for Remix compatability.

## 7.3.13

### Patch Changes

- Fix export names for unpkg / jsdelivr.

## 7.4.0

### Minor Changes

- Return `{ sessionSecureID }` from `H.init` for consumption by Remix SDK
- Persist `sessionSecureID` to `sessionStorage`

## 7.4.1

### Patch Changes

- Ensure compatibility with older browser XHR implementations.

## 7.4.2

### Patch Changes

- Add support for a new `storageMode` setting to avoid using `window.localStorage`.

## 7.4.3

### Patch Changes

- Support passing `recordCrossOriginIframe: false` in a cross-origin iframe to record a session for the iframe contents.

## 7.4.4

### Patch Changes

- Ensure stacktraces from Promises are parsed correctly.

## 7.5.0

### Minor Changes

- Added support to specify `serviceName`.

## 7.5.1

### Patch Changes

- Remove canvas recording logging (not only enabled if the `debug` setting is provided).

## 7.5.2

### Patch Changes

- Auto-inline CSS on 127.0.0.1 (a common alias for localhost).

## 7.5.3

### Patch Changes

- Ensure `H.snapshot()` does not use `setTimeout` to avoid blocking the event loop.

## 7.5.4

### Patch Changes

- Record network request payloads with absolute timestamps.

## 7.5.5

### Patch Changes

- Ensure `H.start()` and `H.stop()` behavior is async-race-safe to avoid inconsistent behavior when `H.stop()` or `H.start()` is called before recording is started.
