# Contributing to the LaunchDarkly JavaScript Observability SDK

LaunchDarkly has published an [SDK contributor's guide](https://docs.launchdarkly.com/sdk/concepts/contributors-guide) that provides a detailed explanation of how our SDKs work. See below for additional information on how to contribute to this SDK.

## Submitting bug reports and feature requests

The LaunchDarkly SDK team monitors the [issue tracker](https://github.com/launchdarkly/observability-sdk/issues) in the SDK repository. Bug reports and feature requests specific to this library should be filed in this issue tracker. The SDK team will respond to all newly filed issues within two business days.

## Submitting pull requests

We encourage pull requests and other contributions from the community. Before submitting pull requests, ensure that all temporary or unintended code is removed. Don't worry about adding reviewers to the pull request; the LaunchDarkly SDK team will add themselves. The SDK team will acknowledge all pull requests within two business days.

## Build instructions

### Prerequisites

`npm` or `yarn` is required to develop in this repository.

We recommend installing the [LaunchDarkly JavaScript SDK](https://github.com/launchdarkly/js-client-sdk) before developing in this repository.

### Setup

To install project dependencies:

```
# npm
npm i @launchdarkly/observability
npm i @launchdarkly/session-replay

# yarn
yarn add @launchdarkly/observability
yarn add @launchdarkly/session-replay

```

### Build

To build all projects, from the root directory:

```
yarn build
```

Running `yarn build` in an individual package (`@launchdarkly/observability` or `@launchdarkly/session-replay`) will build that package, but will not rebuild any dependencies.

### Testing

To run all unit tests:

```shell
# install dependencies
yarn

# lint
yarn format-check

# run all tests
yarn test
```

LaunchDarkly's CI tests will run automatically against all supported versions. To learn more, read [`.github/workflows/turbo.yml`](.github/workflows/turbo.yml).

## Code organization

The library's structure is as follows:

* `sdk/` directory contains all SDKs
  * `@launchdarkly` directory with `sdk/` contains the `@launchdarkly`-prefixed npm packages
* `e2e` directory contains end to end tests exercising the SDKs
* `rrweb` directory hosts the rrweb fork as a git submodule; used by the Session Replay SDK

## Documenting types and methods

Please try to make the style and terminology in documentation comments consistent with other documentation comments in the library. Also, if a class or method is being added that has an equivalent in other libraries, and if we have described it in a consistent away in those other libraries, please reuse the text whenever possible (with adjustments for anything language-specific) rather than writing new text.
