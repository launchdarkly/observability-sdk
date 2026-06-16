# LaunchDarkly Observability SDK for Next.js

[![NPM][o11y-next-npm-badge]][o11y-next-npm-link]

**NB: APIs are subject to change until a 1.x version is released.**

LaunchDarkly observability and session replay for Next.js apps — frontend and
backend errors, logs, traces, metrics, and session replays.

## Standalone mode

There is no LaunchDarkly feature-flag SDK for Next.js, so this package runs the
LaunchDarkly observability and session replay plugins in **standalone mode**:
they are initialized directly from your LaunchDarkly SDK key (client-side ID)
rather than being registered with a LaunchDarkly client. No feature-flag SDK is
required.

## Install

```shell
# npm
npm i @launchdarkly/observability-next

# yarn
yarn add @launchdarkly/observability-next
```

## Usage

### 1. Client (App Router) — `app/layout.tsx`

```tsx
import { LDObservabilityInit, ErrorBoundary } from '@launchdarkly/observability-next/client'

export default function RootLayout({ children }: { children: React.ReactNode }) {
	return (
		<ErrorBoundary>
			<LDObservabilityInit
				sdkKey={process.env.NEXT_PUBLIC_LAUNCHDARKLY_SDK_KEY}
				serviceName="my-nextjs-frontend"
				environment="production"
				tracingOrigins
				networkRecording={{ enabled: true, recordHeadersAndBody: true }}
			/>
			<html lang="en">
				<body>{children}</body>
			</html>
		</ErrorBoundary>
	)
}
```

For the Pages Router, render `<LDObservabilityInit />` in `pages/_app.tsx`.

### 2. Server instrumentation — `instrumentation.ts`

```ts
export async function register() {
	const { registerObservability } = await import('@launchdarkly/observability-next/server')
	await registerObservability({
		sdkKey: process.env.LAUNCHDARKLY_SDK_KEY!,
		serviceName: 'my-nextjs-backend',
		environment: 'production',
	})
}
```

### 3. Wrap route handlers

App Router:

```ts
import { AppRouterObservability } from '@launchdarkly/observability-next/server'

const withObservability = AppRouterObservability({ sdkKey: process.env.LAUNCHDARKLY_SDK_KEY! })

export const GET = withObservability(async (request) => {
	return Response.json({ ok: true })
})
```

Pages Router:

```ts
import { PageRouterObservability } from '@launchdarkly/observability-next/server'

export default PageRouterObservability({ sdkKey: process.env.LAUNCHDARKLY_SDK_KEY! })(
	async (req, res) => {
		res.status(200).send('ok')
	},
)
```

### 4. Link backend traces to sessions — `middleware.ts`

```ts
import { observabilityMiddleware } from '@launchdarkly/observability-next/server'

export async function middleware(request: Request) {
	// Return the response so the forwarded x-highlight-request header reaches
	// downstream route handlers.
	return observabilityMiddleware(request)
}
```

### 5. (Optional) Proxy + config — `next.config.mjs`

```js
import { withLaunchDarklyConfig } from '@launchdarkly/observability-next/config'

/** @type {import('next').NextConfig} */
const nextConfig = {}

export default withLaunchDarklyConfig(nextConfig)
```

This sets up same-origin rewrites that proxy browser telemetry through your
domain to LaunchDarkly (enabled by default). Disable with
`{ configureLaunchDarklyProxy: false }`.

### 6. (Optional) SSR error pages

```tsx
// App Router: app/error.tsx
'use client'
import { appRouterSsrErrorHandler } from '@launchdarkly/observability-next/ssr'

export default appRouterSsrErrorHandler(({ error, reset }) => (
	<button onClick={reset}>Try again</button>
))
```

```tsx
// Pages Router: pages/_error.tsx
import { pageRouterCustomErrorHandler } from '@launchdarkly/observability-next/ssr'

export default pageRouterCustomErrorHandler({
	sdkKey: process.env.NEXT_PUBLIC_LAUNCHDARKLY_SDK_KEY,
})
```

## Manual recording

Use `LDObserve` / `LDRecord` directly:

```ts
import { LDObserve } from '@launchdarkly/observability-next/server' // or /client
LDObserve.recordError(new Error('boom'))
```

[o11y-next-npm-badge]: https://img.shields.io/npm/v/@launchdarkly/observability-next.svg?style=flat-square
[o11y-next-npm-link]: https://www.npmjs.com/package/@launchdarkly/observability-next
