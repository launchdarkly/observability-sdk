# Flutter (Dart AOT) crash symbolication

Release Flutter builds compiled with `--obfuscate` strip Dart names, so crash
stack traces arrive as raw snapshot addresses instead of `Class.method
(file.dart:line)`:

```
os: android arch: arm64 comp: no sim: no
build_id: '0f8a1b2c3d4e5f60718293a4b5c6d7e8'
isolate_dso_base: 7b..., isolate_instructions: 7b...
    #00 abs 0000007b8a1e2cbf virt 0000000000265cbf _kDartIsolateSnapshotInstructions+0x1cbf
    #01 abs 0000007b8a1e31a3 virt 00000000002661a3 _kDartIsolateSnapshotInstructions+0x21a3
```

To turn those addresses back into functions + `file:line`, upload the debug
symbols file that `flutter build` produced for the exact binary you shipped. The
LaunchDarkly backend then resolves each frame (including inlined calls) using the
uploaded map.

This mirrors the Apple dSYM flow (`../../TestApp/SYMBOLICATION.md` in the Swift
SDK): both compile per-arch DWARF into a compact `.dartmap`/`.dsymmap` symbol map
that the backend queries by address.

## How lookup is keyed (two lanes)

An uploaded map is stored under two keys so the backend can find it however the
crash is addressed:

- **Id lane (primary):** `{projectId}/_sym/flutter/id/<symbols_id>/app.dartmap`,
  where `<symbols_id>` is the Dart snapshot **build id** — the `build_id:` value
  printed in every obfuscated trace. The SDK reports it as the
  `launchdarkly.symbols_id` resource attribute, and the backend also recovers it
  straight from the crash text, so this lane works even if the attribute is
  missing. A build id is unique per (build, arch), so no version is needed.
- **Version lane (fallback):** `{projectId}/<app-version>/app.<os>-<arch>.dartmap`,
  used when a build id can't be matched. Requires `--app-version` at upload time
  to equal the app's reported `service.version`.

The Id lane is the reliable path; you generally only need `--app-version` if you
want the version-lane fallback too.

## Prerequisites

- `ldcli` built with symbol support (`go build ./cmd/ldcli` in the `ldcli` repo).
- A LaunchDarkly project key and API access token for `ldcli`.
- The example app configured with your mobile key (see `README`/`dart_defines.json`).

## 1. Build an obfuscated release with split debug info

`--split-debug-info` writes one `app.<platform>.symbols` file per architecture;
`--obfuscate` is what makes symbolication necessary in the first place.

```bash
# from example/
flutter build apk \
  --obfuscate \
  --split-debug-info=build/symbols \
  --dart-define GIT_SHA=$(git rev-parse HEAD) \
  --dart-define-from-file=dart_defines.json
```

(For iOS, use `flutter build ipa` with the same flags.) After the build,
`build/symbols/` contains e.g. `app.android-arm64.symbols`,
`app.android-arm.symbols`, `app.android-x64.symbols`. These are git-ignored.

## 2. Upload the symbols

```bash
# from the ldcli repo (or wherever ldcli is on your PATH)
ldcli symbols upload \
  --type flutter \
  --path <path-to>/example/build/symbols \
  --project <your-project-key> \
  --access-token <your-api-token>
```

`--type dart` is accepted as an alias for `--type flutter`.

`ldcli` reads each `.symbols` ELF, extracts its build id (`symbols_id`) and DWARF,
compiles them to `app.dartmap`, and uploads one per architecture to the Id lane.
Add `--app-version $(git rev-parse HEAD)` (matching the `GIT_SHA` dart-define
above) to also populate the Version lane.

To inspect what would be uploaded without sending it, use
`ldcli symbols generate --type flutter --path build/symbols --out ./out`; the
`out/` tree mirrors the storage keys described above.

## 3. Run the exact build and trigger a crash/error

Install and run the **same** obfuscated binary you generated symbols for (the
build id must match). On the home screen:

- **Trigger Crash** — throws `StateError('Failed to connect to bogus server.')`,
  reported via the app's `FlutterError.onError` / zone guard.
- **Trigger Error** — calls `LDObserve.recordException(...)` directly.

Both send the obfuscated stack trace as the error's `exception.stacktrace`.

## 4. Verify symbolication in the dashboard

Open the error in the LaunchDarkly Observability UI. The mapped stack frames
should now show Dart function names and `file.dart:line` instead of
`_kDartIsolateSnapshotInstructions+0x...`, with inlined calls expanded.

If frames are still raw:

- Confirm the running binary's `build_id:` matches a `symbols_id` you uploaded
  (compare the `build_id` in the crash text to `ldcli`'s upload log).
- Confirm you uploaded the arch the device runs (e.g. `android-arm64`).
- For the Version-lane fallback, confirm `--app-version` equals the app's
  `service.version` (`GIT_SHA` here).

## Notes

- **Keep the symbols.** Archive `build/symbols/` for every release you ship;
  without the matching file a crash from that build can't be symbolicated.
- **Web is out of scope.** `dart2js`/web builds don't produce AOT snapshot
  traces or a build id, so this flow doesn't apply to them.
