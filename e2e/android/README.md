# Android Observability e2e app

Demo app for the LaunchDarkly Android Observability SDK. Its home screen exercises
spans, logs, and error reporting so you can see how each signal shows up in
LaunchDarkly.

It also demonstrates **Android Java/Kotlin symbolication (the "Symbols Id Lane")**:
R8 obfuscates release builds, and obfuscated crash/error stack traces are retraced
on the backend using the uploaded `mapping.txt`, keyed by a deterministic symbols
id.

## Symbolication (readable stack traces)

Release builds are obfuscated by R8, so stack traces look like
`at g5.g.invoke(SourceFile:57)` until the `mapping.txt` is uploaded to
LaunchDarkly. See **[SYMBOLICATION.md](./SYMBOLICATION.md)** for the full
walkthrough — building an obfuscated release, uploading with
`ldcli symbols upload --type android`, and triggering an obfuscated error to
verify retracing.
