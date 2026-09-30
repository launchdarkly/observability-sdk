# Testing deferred start

Scratch notes for exercising `isEnabled: false` + a later `startSessionReplay()` by hand.
This branch is a test harness, not for merge — the fix itself is in
[#785](https://github.com/launchdarkly/observability-sdk/pull/785).

## What is being tested

`createSessionReplayPlugin({ isEnabled: false })` should initialize native session replay
*without* recording, and a later `startSessionReplay()` should turn recording on.

Before the fix that never recorded: `startSessionReplay()` re-applied the configured
`isEnabled`, writing the `false` straight back (iOS) or calling `LDReplay.stop()` (Android).

## Setup

`example/.env` needs a real mobile key — **recordings land in whatever environment it points
at**, so use a test environment if you don't want noise in production:

```sh
cd sdk/@launchdarkly/react-native-ld-session-replay/example
cp .env.example .env   # then set LAUNCHDARKLY_MOBILE_KEY=mob-...
yarn install && npx pod-install ios   # iOS only
yarn ios     # or: yarn android
```

The native adapters changed on this branch, so a **full rebuild is required**. A Metro reload
is not enough — the fix lives in the native binary.

## What you should see

The app shows a status bar under the architecture banner:

| state | meaning |
| --- | --- |
| `Replay: not recording (isEnabled: false)` (grey) | plugin initialized, recording deferred — the starting state |
| `Replay: RECORDING` (green) | `startSessionReplay()` resolved |
| `Replay: start FAILED` (red) | the start was rejected, with the reason underneath |

Tap **Start replay**. Grey should become green, and the displayed `session.id` is what to look
for in the dashboard.

That red state is itself new: before the fix a failed start still resolved successfully, so
the adapters now report *why* a start didn't record.

## Confirming it actually recorded

The UI only reflects that the promise resolved. For proof, watch the native logs:

The adapters log under an `[SR init]` prefix. Note that these are `NSLog` / plain-logger
calls from the app process, so filter by **process**, not by the `com.launchdarkly`
subsystem — that subsystem only carries the SDK's own lines, and would miss `[SR init]`:

```sh
# iOS (simulator)
xcrun simctl spawn booted log stream --style compact \
  --predicate 'process == "SessionReplayReactNativeExample" AND (subsystem BEGINSWITH "com.launchdarkly" OR eventMessage CONTAINS "SR init")'

# Android
adb logcat | grep -E 'SR init|LaunchDarkly'
```

On launch, and then after tapping Start, expect:

```
[SR init] configure: ... enabled=false, sampleRate=1.0
[SR init] start: leaving recording off (isEnabled=false)   <- init honored the config
[SR init] start: already initialized, forceEnable=true     <- explicit start overrode it
```

Two things make this convincing. There should be exactly **one** `configure:` line — the old
workaround needed a second `configureSessionReplay({ isEnabled: true })` call, and its absence
is the point of the fix. And no `skipped by sampling` line should appear (the exact casing
differs by platform, so grep case-insensitively).

Since "no error logged" is weak evidence, verify the logging actually works by setting
`sampleRate: 0` in `replayOptions` (in `src/App.tsx`), rebuilding, and confirming the
`skipped by sampling` line *does* appear. Then put it back to `1.0`.

## Worth poking at

- **Start twice.** The second resolves as already-started; it should not restart or duplicate.
- **Stop, then Start.** Resumes recording. Note sampling is re-rolled for the new enable
  cycle, so at `sampleRate: 1.0` it always comes back.
- **Start, then Soft reload.** This is the quieter bug that was also fixed: the native
  instance outlives the JS runtime, so plugin setup re-runs with `isEnabled: false`. Recording
  should **keep going** — previously the re-init silently stopped it. `JS load` changes while
  `session.id` stays.
- **Start before login.** Recording persists across `identify()`.
