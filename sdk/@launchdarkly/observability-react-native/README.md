# LaunchDarkly React Native Observability Plugin

[![NPM][o11y-sdk-npm-badge]][o11y-sdk-npm-link]
[![Actions Status][o11y-sdk-ci-badge]][o11y-sdk-ci]
[![NPM][o11y-sdk-dm-badge]][o11y-sdk-npm-link]
[![NPM][o11y-sdk-dt-badge]][o11y-sdk-npm-link]
[![Documentation](https://img.shields.io/static/v1?label=GitHub+Pages&message=API+reference&color=00add8)][o11y-docs-link]

**NB: APIs are subject to change until a 1.x version is released.**

## Installation

### Basic Installation

```bash
npm install @launchdarkly/observability-react-native
# or
yarn add @launchdarkly/observability-react-native
```

## Usage

```typescript
import { LDClient } from '@launchdarkly/react-native-client-sdk';
import { Observability } from '@launchdarkly/observability-react-native';

const client = new LDClient(
  mobileKey,
  user,
  {
    // ... other options
  },
  [
    new Observability({
      serviceName: 'my-react-native-app',
      serviceVersion: '1.0.0',
    })
  ]
);
```

### Manual tracing

Use the `LDObserve` singleton to create spans by hand. `withSpan` runs your
callback inside a span and ends it automatically — even across `await`s, where
React Native only tracks the active context synchronously. Use `scope.child` to
parent nested spans off the captured context:

```typescript
import { LDObserve } from '@launchdarkly/observability-react-native';

await LDObserve.withSpan('LoadProducts', async (scope) => {
  scope.span.setAttribute('source', 'api');

  // `scope.child` parents off LoadProducts even after the await above.
  const products = await scope.child('FetchFromApi', async (fetchScope) => {
    const response = await fetch('https://api.example.com/products');
    fetchScope.span.setAttribute('http.status_code', response.status);
    return response.json();
  });

  scope.span.setAttribute('product_count', products.length);
});
```

### Using the OpenTelemetry API directly

If you prefer to follow the standard [OpenTelemetry JS](https://opentelemetry.io/docs/languages/js/)
documentation, or you need to hand a `Tracer` to a third-party library,
`LDObserve.getTracer()` returns an [`LDTracer`](https://launchdarkly.github.io/observability-sdk/sdk/@launchdarkly/observability-react-native/interfaces/LDTracer.html)
— a standard OpenTelemetry [`Tracer`](https://open-telemetry.github.io/opentelemetry-js/interfaces/_opentelemetry_api.Tracer.html)
plus the async-safe `withSpan` helper. It is wired to the same exporter and
sampler as the rest of the SDK. It is always safe to call — before the SDK
finishes initializing (or when `disableTraces` is set) it returns a no-op tracer.

```typescript
import { LDObserve } from '@launchdarkly/observability-react-native';

const tracer = LDObserve.getTracer();

// Standard OpenTelemetry API
const span = tracer.startSpan('checkout');
span.setAttribute('cart.id', 'cart-7');
span.end();

// Async-safe nested spans (React Native) — same helper as LDObserve.withSpan
await tracer.withSpan('LoadProducts', async (scope) => {
  const products = await scope.child('FetchFromApi', async (fetchScope) => {
    const response = await fetch('https://api.example.com/products');
    fetchScope.span.setAttribute('http.status_code', response.status);
    return response.json();
  });
  scope.span.setAttribute('product_count', products.length);
});
```

> **Context propagation in React Native.** React Native tracks the active span
> only synchronously (there is no `AsyncLocalStorage`), so it is not restored
> after an `await`. Use `tracer.withSpan` / `scope.child` (or
> `LDObserve.withSpan`) for nested async work. See the
> [Tracing Guide](guides/tracing.md) for details.

## Guides

- [Tracing Guide](guides/tracing.md) — a cookbook of common tracing patterns (spans, nested operations, error handling, correlated logs, and end-to-end mobile-to-backend traces).

## About LaunchDarkly

- LaunchDarkly Observability provides a way to collect and send errors, logs, traces to LaunchDarkly. Correlate latency or exceptions with your releases to safely ship code.
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

[o11y-sdk-ci-badge]: https://github.com/launchdarkly/observability-sdk/actions/workflows/turbo.yml/badge.svg
[o11y-sdk-ci]: https://github.com/launchdarkly/observability-sdk/actions/workflows/turbo.yml
[o11y-sdk-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/observability-react-native.svg?style=flat-square
[o11y-sdk-npm-link]: https://www.npmjs.com/package/@launchdarkly/observability-react-native
[o11y-sdk-dm-badge]: https://img.shields.io/npm/dm/@launchdarkly/observability-react-native.svg?style=flat-square
[o11y-sdk-dt-badge]: https://img.shields.io/npm/dt/@launchdarkly/observability-react-native.svg?style=flat-square
[o11y-docs-link]: https://launchdarkly.github.io/observability-sdk/sdk/@launchdarkly/observability-react-native/
