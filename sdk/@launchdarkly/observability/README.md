# LaunchDarkly JavaScript Observability SDK for Browsers

[![NPM][o11y-sdk-npm-badge]][o11y-sdk-npm-link]
[![Actions Status][o11y-sdk-ci-badge]][o11y-sdk-ci]
[![NPM][o11y-sdk-dm-badge]][o11y-sdk-npm-link]
[![NPM][o11y-sdk-dt-badge]][o11y-sdk-npm-link]

**NB: APIs are subject to change until a 1.x version is released.**

## Install

Install the package

```shell
# npm
npm i @launchdarkly/observability

# yarn
yarn add @launchdarkly/observability
```

Update your web app entrypoint.

```tsx
import { initialize } from 'launchdarkly-js-client-sdk'
import Observability, { LDObserve } from '@launchdarkly/observability'

const client = init3(
	'<CLIENT_SIDE_ID>',
	{ key: 'authenticated-user@example.com' },
	{
		// Not including plugins at all would be equivalent to the current LaunchDarkly SDK.
		plugins: [
			new Observability({
				networkRecording: {
					enabled: true,
					recordHeadersAndBody: true,
				},
			}),
		],
	},
)
```

## Getting started

Refer to the [SDK documentation](https://docs.launchdarkly.com/sdk/client-side/javascript#getting-started) for instructions on getting started with using the SDK.

## Adding attributes to all telemetry

Use `LDObserve.setAttributes` to tag everything recorded afterwards with values known only after start-up, such as the campaign that brought the user in. The attributes are added to every span (including document load, fetch/XHR, click and page view spans), log, error and metric.

```ts
LDObserve.setAttributes({ utm_campaign: 'spring_promo', utm_source: 'email' })

// Later: stop sending a key. A `null` value in setAttributes does the same.
LDObserve.removeAttributes(['utm_campaign'])
```

- Attributes are kept in memory for the current page load only. They are never persisted, so after a full page load, call `setAttributes` again (persist the values yourself if you need them to survive the load).
- Calls made before the SDK initializes are applied once it does.
- Attributes passed to an individual call (log metadata, span or metric attributes) take precedence. Keys in the namespaces the SDK sets itself (`highlight.*`, `launchdarkly.*`, `session.*`, `context.contextKeys.*`) are ignored.
- Values can be strings, numbers, booleans or arrays of those.
- Every attribute is also added to metrics, so an attribute with many distinct values creates as many metric series.

LaunchDarkly context keys from `identify` (`context.contextKeys.<kind>`) are added to every span in the same way.

## Session continuity

A session continues across full page loads on the same origin until it has been idle for 15 minutes; a full page load on a different origin starts a new session.

When a full page load starts a new session instead of continuing the stored one (for example, because it has been idle past the timeout), the new session's telemetry carries `session.previous_id`: the ID of the session it replaced. Use it to chain related sessions in analysis, for example to join events before and after a long idle gap.

Session storage options:

- `sessionCookie: true` — also store the session in a cookie (expiring 15 minutes after it is last written). This can help with cookie-consent compliance.
- `sessionCookieDomain: 'example.com'` — set the session cookie's `Domain` attribute so the session can be continued across your subdomains (e.g. `app.example.com` and `checkout.example.com`). Requires `sessionCookie: true`. By default, the cookie is scoped to the exact host that created it.
- `storageMode: 'sessionStorage'` — keep the session in `sessionStorage` instead of `localStorage`, so each tab has its own session and closing the tab ends it.

### Continuing a session across domains

Storage is per-origin, so a flow that moves a user from one of your domains to another (for example from a landing site, through a hosted identity provider, to your app on a different domain) starts a new session on the second domain. To link the two, generate a `sessionKey` on the first domain, pass it to the SDK there, carry it through the redirect (for example in the OAuth `state` parameter), and pass the same value to the SDK on the second domain. When no unexpired session exists yet, the SDK derives the session ID from the `sessionKey`, so both domains report the same session.

```ts
// First domain, before redirecting:
const sessionKey = crypto.randomUUID()
new Observability({ sessionKey })

// Second domain, after reading the key back from the OAuth `state`:
new Observability({ sessionKey: keyFromState })
```

Only pass keys you generate and control: anyone who knows a `sessionKey` can continue that session.

The `@launchdarkly/session-replay` SDK accepts the same options.

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
[o11y-sdk-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/observability.svg?style=flat-square
[o11y-sdk-npm-link]: https://www.npmjs.com/package/@launchdarkly/observability
[o11y-sdk-dm-badge]: https://img.shields.io/npm/dm/@launchdarkly/observability.svg?style=flat-square
[o11y-sdk-dt-badge]: https://img.shields.io/npm/dt/@launchdarkly/observability.svg?style=flat-square
