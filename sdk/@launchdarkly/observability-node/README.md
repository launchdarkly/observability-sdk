# LaunchDarkly Node.js Server-Side Observability Plugin

[![NPM][o11y-sdk-npm-badge]][o11y-sdk-npm-link]
[![Actions Status][o11y-sdk-ci-badge]][o11y-sdk-ci]
[![NPM][o11y-sdk-dm-badge]][o11y-sdk-npm-link]
[![NPM][o11y-sdk-dt-badge]][o11y-sdk-npm-link]
[![Documentation](https://img.shields.io/static/v1?label=GitHub+Pages&message=API+reference&color=00add8)][o11y-docs-link]

**NB: APIs are subject to change until a 1.x version is released.**

## Install

Install the package
```shell
# npm
npm i @launchdarkly/observability-node

# yarn
yarn add @launchdarkly/observability-node
```

Update your application to instantiate the observability plugin when you initialize the LaunchDarkly SDK.
```TypeScript
import { init } from '@launchdarkly/node-server-sdk'
import { Observability } from '@launchdarkly/observability-node'

const client = init(
  'sdk-key',
  {
    plugins: [
      new Observability(),
    ],
  },
)
```

## Instrumenting libraries loaded with `import`

The plugin instruments libraries such as `pg`, `ioredis` and `express` by patching them as they load. Libraries loaded with `require()` are patched automatically. Libraries loaded with `import` can only be patched by a Node.js loader hook, which has to be registered before your application starts. Register it when:

- your application is an ES module (for example, a NestJS 12 project, which uses ES modules by default), or
- a dependency loads an instrumented library with `import` (for example, MikroORM 7 loads `pg` this way), even if your application is CommonJS.

Start Node.js with the `register` entry point:

```shell
node --import @launchdarkly/observability-node/register dist/main.js

# or, when you can't change the command line
NODE_OPTIONS="--import @launchdarkly/observability-node/register" node dist/main.js
```

Keep the `Observability` plugin in your LaunchDarkly client configuration; the entry point only installs the hooks. It also loads the instrumentations before your application code, so libraries are patched even when they are imported before the LaunchDarkly client is initialized. This requires Node.js 18.19, 20.6 or later.

## Manual instrumentation with LDObserve

The `Observability` plugin automatically captures flag evaluations, logs, and traces. Use the `LDObserve` singleton when you want to record custom metrics, logs, errors, or spans so they are correlated with your LaunchDarkly data.

```TypeScript
import { LDObserve } from '@launchdarkly/observability-node'

// Record custom metrics (counters, gauges, histograms, etc.)
LDObserve.recordCount({ name: 'api_calls', value: 1 })
LDObserve.recordHistogram({ name: 'response_time_ms', value: 150 })

// Record logs and errors
LDObserve.recordLog('User performed action', 'info', undefined, undefined, { user_id: '12345' })
LDObserve.recordError(new Error('Payment failed'), undefined, undefined, { component: 'checkout' })

// Create spans tied to request context (e.g. in Express)
app.get('/order', (req, res) => {
  LDObserve.runWithHeaders('handle-order', req.headers, (span) => {
    // Your handler logic; span is linked to the request trace
    res.json({ orderId: '123' })
  })
})
```

## Getting started

Refer to the [SDK documentation](https://launchdarkly.com/docs/sdk/server-side/node-js#get-started) for instructions on getting started with using the SDK.

## Verifying SDK build provenance with the SLSA framework

LaunchDarkly uses the [SLSA framework](https://slsa.dev/spec/v1.0/about) (Supply-chain Levels for Software Artifacts) to help developers make their supply chain more secure by ensuring the authenticity and build integrity of our published SDK packages. To learn more, see the [provenance guide](PROVENANCE.md).

## About LaunchDarkly

- LaunchDarkly Observability provies a way to collect and send errors, logs, traces to LaunchDarkly. Correlate latency or exceptions with your releases to safely ship code.
- LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard. With LaunchDarkly, you can:
    - Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    - Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    - Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    - Grant access to certain features based on user attributes, like payment plan (eg: users on the ‘gold’ plan get access to more features than users in the ‘silver’ plan).
    - Disable parts of your application to facilitate maintenance, without taking everything offline.
- LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Read [our documentation](https://docs.launchdarkly.com/sdk) for a complete list.
- Explore LaunchDarkly
    - [launchdarkly.com](https://www.launchdarkly.com/ 'LaunchDarkly Main Website') for more information
    - [docs.launchdarkly.com](https://docs.launchdarkly.com/ 'LaunchDarkly Documentation') for our documentation and SDK reference guides
    - [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/ 'LaunchDarkly API Documentation') for our API documentation
    - [blog.launchdarkly.com](https://blog.launchdarkly.com/ 'LaunchDarkly Blog Documentation') for the latest product updates

[o11y-sdk-ci-badge]: https://github.com/launchdarkly/observability-sdk/actions/workflows/turbo.yml/badge.svg
[o11y-sdk-ci]: https://github.com/launchdarkly/observability-sdk/actions/workflows/turbo.yml
[o11y-sdk-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/observability-node.svg?style=flat-square
[o11y-sdk-npm-link]: https://www.npmjs.com/package/@launchdarkly/observability-node
[o11y-sdk-dm-badge]: https://img.shields.io/npm/dm/@launchdarkly/observability-node.svg?style=flat-square
[o11y-sdk-dt-badge]: https://img.shields.io/npm/dt/@launchdarkly/observability-node.svg?style=flat-square
[o11y-docs-link]: https://launchdarkly.github.io/observability-sdk/sdk/@launchdarkly/observability-node/