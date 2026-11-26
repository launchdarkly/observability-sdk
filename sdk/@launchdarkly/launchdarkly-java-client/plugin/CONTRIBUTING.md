# Contributing

## Build and test

The plugin builds against the client-side Java SDK, which is not on Maven Central yet. Install it
from a checkout of java-client-sdk first:

```sh
(cd path/to/java-client-sdk && ./gradlew :launchdarkly-java-client-sdk:publishToMavenLocal)
./gradlew check                       # tests, coverage gate, POM and classpath checks
./gradlew check -PtestJavaVersion=8   # the same tests on Java 8
```

Gradle runs on JDK 17 or later; the plugin targets Java 8.

`check` fails when:
- line coverage falls below 99% or branch coverage below 98%;
- the published POM depends on anything but OpenTelemetry and OkHttp, or on a LaunchDarkly
  artifact, or lacks the metadata Maven Central requires;
- OkHttp 5 reaches the runtime classpath. The SDK ships OkHttp 4.12, and OpenTelemetry 1.51.0 is
  the last release whose OkHttp sender uses OkHttp 4, so OpenTelemetry stays there until the SDK
  moves to OkHttp 5.

The desktop tests create Swing windows, so they need a display. CI runs them under `xvfb-run`; on a
machine without a display they skip themselves, and the coverage gate then fails.

The sampling tests run scenarios shared with the other LaunchDarkly SDKs. The files in
`src/test/resources/sampling` are copies of `go/internal/otel/*-test-scenarios.json` at the root
of this repository; update them from there.

## CI

`.github/workflows/java-client-observability.yml`, at the root of this repository, runs on every
pull request and push to `main` that touches this folder. It runs `check` and `javadoc` on Java 8,
11, 17 and 21, and builds the [demo app](../demo) against the plugin from source and runs its
offline smoke tests.

## Releases

Releases are made by [release-please](https://github.com/googleapis/release-please) from
[Conventional Commits](https://www.conventionalcommits.org/) on `main`, as for the other packages
in this repository. This folder is the package `launchdarkly-java-client-sdk-observability` in
`release-please-config.json`; changes to `../demo` do not release it. While the version is below
1.0, a feature raises the minor version and a fix the patch version.

release-please keeps a release pull request open that updates `gradle.properties` and
`CHANGELOG.md`. Merging it tags the release, and `.github/workflows/release-please.yml`:
1. runs `check` and publishes the plugin and its Compose library, signed, to Maven Central through
   Sonatype's Central Portal, under the `com.launchdarkly` namespace
   (`.github/actions/publish-java-client-sdk`);
2. generates SLSA provenance for the jars and attaches it to the GitHub release;
3. publishes the release, which starts as a draft.

Publish the SDK before the first release of the plugin: the plugin does not depend on the SDK in
its POM, so it can be published first, but nothing can use it until the SDK is on Maven Central.
