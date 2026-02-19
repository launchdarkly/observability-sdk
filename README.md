# LaunchDarkly Observability SDK

## Overview

This repository houses the LaunchDarkly Observability and Session Replay SDKs across multiple languages and platforms.

## Packages

| Package | Package manager | Tests |
| ------- | ---------------- | ----- |
| [Go SDK](go/README.md) | [![Go Reference][go-pkg-badge]][go-pkg-link] | [![Actions Status][go-ci-badge]][go-ci] |
| [@launchdarkly/observability](sdk/@launchdarkly/observability/README.md) (browser) | [![NPM][o11y-sdk-npm-badge]][o11y-sdk-npm-link] | [![Actions Status][o11y-sdk-ci-badge]][o11y-sdk-ci] |
| [@launchdarkly/observability-node](sdk/@launchdarkly/observability-node/README.md) | [![NPM][o11y-node-npm-badge]][o11y-node-npm-link] | [![Actions Status][o11y-sdk-ci-badge]][o11y-sdk-ci] |
| [@launchdarkly/observability-react-native](sdk/@launchdarkly/observability-react-native/README.md) | [![NPM][o11y-rn-npm-badge]][o11y-rn-npm-link] | [![Actions Status][o11y-sdk-ci-badge]][o11y-sdk-ci] |
| [@launchdarkly/session-replay](sdk/@launchdarkly/session-replay/README.md) | [![NPM][session-replay-sdk-npm-badge]][session-replay-sdk-npm-link] | [![Actions Status][o11y-sdk-ci-badge]][o11y-sdk-ci] |
| [launchdarkly-observability](sdk/@launchdarkly/observability-python/README.md) (Python) | [![PyPI][o11y-pypi-badge]][o11y-pypi-link] | [![Actions Status][python-ci-badge]][python-ci] |
| [LaunchDarkly.Observability](sdk/@launchdarkly/observability-dotnet/README.md) (.NET) | [![NuGet][o11y-nuget-badge]][o11y-nuget-link] | [![Actions Status][dotnet-ci-badge]][dotnet-ci] |
| [launchdarkly-observability-android](sdk/@launchdarkly/observability-android/README.md) | [![Maven Central][o11y-android-badge]][o11y-android-link] | [![Actions Status][android-ci-badge]][android-ci] |
| [launchdarkly_flutter_observability](sdk/@launchdarkly/launchdarkly_flutter_observability/README.md) | [![pub package][o11y-flutter-badge]][o11y-flutter-link] | — |

## Getting started

Pick a package from the table above and follow its README or the [Docs](https://docs.launchdarkly.com/) link for install and usage.

## Verifying SDK build provenance with the SLSA framework

LaunchDarkly uses the [SLSA framework](https://slsa.dev/spec/v1.0/about) (Supply-chain Levels for Software Artifacts) to help developers make their supply chain more secure by ensuring the authenticity and build integrity of our published SDK packages. To learn more, see the provenance guide (PROVENANCE.md).

## About LaunchDarkly

- LaunchDarkly Observability provides a way to collect and send errors, logs, traces to LaunchDarkly. Correlate latency or exceptions with your releases to safely ship code.
- LaunchDarkly Session Replay provides a way to capture user sessions on your application to replay them in LaunchDarkly. Understand how users are interacting with your site and with new features you ship.
- LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard. With LaunchDarkly, you can:
    - Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    - Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    - Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    - Grant access to certain features based on user attributes, like payment plan (eg: users on the 'gold' plan get access to more features than users in the 'silver' plan).
    - Disable parts of your application to facilitate maintenance, without taking everything offline.
- LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Read [our documentation](https://docs.launchdarkly.com/sdk) for a complete list.
- Explore LaunchDarkly
    - [launchdarkly.com](https://www.launchdarkly.com/ 'LaunchDarkly Main Website') for more information
    - [docs.launchdarkly.com](https://docs.launchdarkly.com/ 'LaunchDarkly Documentation') for our documentation and SDK reference guides
    - [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/ 'LaunchDarkly API Documentation') for our API documentation
    - [blog.launchdarkly.com](https://blog.launchdarkly.com/ 'LaunchDarkly Blog Documentation') for the latest product updates

## Contributions

We welcome PRs and issues on this repo. Please don't hesitate to file a ticket if there is anything we can do to help!

[o11y-sdk-ci-badge]: https://github.com/launchdarkly/observability-sdk/actions/workflows/turbo.yml/badge.svg
[o11y-sdk-ci]: https://github.com/launchdarkly/observability-sdk/actions/workflows/turbo.yml
[o11y-sdk-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/observability.svg?style=flat-square
[o11y-sdk-npm-link]: https://www.npmjs.com/package/@launchdarkly/observability
[session-replay-sdk-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/session-replay.svg?style=flat-square
[session-replay-sdk-npm-link]: https://www.npmjs.com/package/@launchdarkly/session-replay
[go-pkg-badge]: https://img.shields.io/badge/go-reference-00ADD8?style=flat-square&logo=go
[go-pkg-link]: https://pkg.go.dev/github.com/launchdarkly/observability-sdk/go
[o11y-node-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/observability-node.svg?style=flat-square
[o11y-node-npm-link]: https://www.npmjs.com/package/@launchdarkly/observability-node
[o11y-rn-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/observability-react-native.svg?style=flat-square
[o11y-rn-npm-link]: https://www.npmjs.com/package/@launchdarkly/observability-react-native
[o11y-pypi-badge]: https://img.shields.io/pypi/v/launchdarkly-observability.svg?style=flat-square
[o11y-pypi-link]: https://pypi.org/project/launchdarkly-observability/
[o11y-nuget-badge]: https://img.shields.io/nuget/v/LaunchDarkly.Observability.svg?style=flat-square
[o11y-nuget-link]: https://www.nuget.org/packages/LaunchDarkly.Observability
[o11y-android-badge]: https://img.shields.io/maven-central/v/com.launchdarkly/launchdarkly-observability-android.svg?style=flat-square
[o11y-android-link]: https://central.sonatype.com/artifact/com.launchdarkly/launchdarkly-observability-android
[o11y-flutter-badge]: https://img.shields.io/pub/v/launchdarkly_flutter_observability.svg?style=flat-square
[o11y-flutter-link]: https://pub.dev/packages/launchdarkly_flutter_observability
[go-ci-badge]: https://github.com/launchdarkly/observability-sdk/actions/workflows/go-plugin.yml/badge.svg
[go-ci]: https://github.com/launchdarkly/observability-sdk/actions/workflows/go-plugin.yml
[python-ci-badge]: https://github.com/launchdarkly/observability-sdk/actions/workflows/python-plugin.yml/badge.svg
[python-ci]: https://github.com/launchdarkly/observability-sdk/actions/workflows/python-plugin.yml
[dotnet-ci-badge]: https://github.com/launchdarkly/observability-sdk/actions/workflows/dotnet-plugin.yml/badge.svg
[dotnet-ci]: https://github.com/launchdarkly/observability-sdk/actions/workflows/dotnet-plugin.yml
[android-ci-badge]: https://github.com/launchdarkly/observability-sdk/actions/workflows/android-observability.yml/badge.svg
[android-ci]: https://github.com/launchdarkly/observability-sdk/actions/workflows/android-observability.yml
