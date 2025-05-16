# LaunchDarkly JavaScript Observability SDK

[![Actions Status][o11y-sdk-ci-badge]][o11y-sdk-ci]

## Overview

This repository houses the JavaScript SDKs for LaunchDarkly Observability. This repo contains the code for our supported JavaScript environments, providing tools for session replay and observability.

## Supported SDKs

### LaunchDarkly JavaScript Observability SDK for Browsers (`@launchdarkly/observability`)

[![NPM][o11y-sdk-npm-badge]][o11y-sdk-npm-link]
[![NPM][o11y-sdk-dm-badge]][o11y-sdk-npm-link]
[![NPM][o11y-sdk-dt-badge]][o11y-sdk-npm-link]

> [!CAUTION]
> This library is an alpha version and should not be considered ready for production use while this message is visible.

#### Install

```shell
# npm
npm i @launchdarkly/observability

# yarn
yarn add @launchdarkly/observability
```

#### Usage

Update your web app entrypoint:
```tsx
import { initialize } from 'launchdarkly-js-client-sdk'
import Observability, { LDObserve } from '@launchdarkly/observability'

const client = init3(
        '<CLIENT_SIDE_ID>',
        { key: 'authenticated-user@example.com' },
        {
          // Not including plugins at all would be equivalent to the current LaunchDarkly SDK.
          plugins: [
            new Observability('<OBSERVABILITY_PROJECT_ID>', {
              networkRecording: {
                enabled: true,
                recordHeadersAndBody: true,
              },
            }),
          ],
        },
)
```

### LaunchDarkly JavaScript Session Replay SDK for Browsers (`@launchdarkly/session-replay`)

[![NPM][session-replay-sdk-npm-badge]][session-replay-sdk-npm-link]
[![NPM][session-replay-sdk-dm-badge]][session-replay-sdk-npm-link]
[![NPM][session-replay-sdk-dt-badge]][session-replay-sdk-npm-link]

> [!CAUTION]
> This library is an alpha version and should not be considered ready for production use while this message is visible.

#### Install

```shell
# npm
npm i @launchdarkly/session-replay

# yarn
yarn add @launchdarkly/session-replay
```

#### Usage

Update your web app entrypoint:
```tsx
import { initialize } from 'launchdarkly-js-client-sdk'
import SessionReplay, { LDRecord } from '@launchdarkly/session-replay'

const client = init3(
        '<CLIENT_SIDE_ID>',
        { key: 'authenticated-user@example.com' },
        {
          // Not including plugins at all would be equivalent to the current LaunchDarkly SDK.
          plugins: [
            new SessionReplay('<OBSERVABILITY_PROJECT_ID>', {
              serviceName: 'example-svc',
            }), // Could be omitted for customers who cannot use session replay.
          ],
        },
)
```

## Getting started

Refer to the [SDK documentation](https://docs.launchdarkly.com/sdk/client-side/javascript#getting-started) for instructions on getting started with using the SDK.

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
[o11y-sdk-dm-badge]: https://img.shields.io/npm/dm/@launchdarkly/observability.svg?style=flat-square
[o11y-sdk-dt-badge]: https://img.shields.io/npm/dt/@launchdarkly/observability.svg?style=flat-square
[session-replay-sdk-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/session-replay.svg?style=flat-square
[session-replay-sdk-npm-link]: https://www.npmjs.com/package/@launchdarkly/session-replay
[session-replay-sdk-dm-badge]: https://img.shields.io/npm/dm/@launchdarkly/session-replay.svg?style=flat-square
[session-replay-sdk-dt-badge]: https://img.shields.io/npm/dt/@launchdarkly/session-replay.svg?style=flat-square
