# nextjs-ld

Example Next.js (App Router) app instrumented with
[`@launchdarkly/observability-next`](../../sdk/@launchdarkly/observability-next).

It runs the LaunchDarkly observability and session replay plugins in
**standalone mode** — initialized directly from a LaunchDarkly SDK key, with no
feature-flag client.

## What it demonstrates

- `src/app/layout.tsx` — `LDObservabilityInit` + `ErrorBoundary` (client)
- `src/instrumentation.ts` — `registerObservability` (server)
- `src/app/api/test/route.ts` — `AppRouterObservability` route wrapper
- `src/app/error.tsx` — `appRouterSsrErrorHandler`
- `middleware.ts` — `observabilityMiddleware`
- `next.config.mjs` — `withLaunchDarklyConfig`

## Run

```bash
# Set NEXT_PUBLIC_LAUNCHDARKLY_CLIENT_SIDE_ID and LAUNCHDARKLY_SDK_KEY in .env
yarn workspace nextjs-ld dev
```
