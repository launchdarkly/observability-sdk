# nextjs-ld

Example Next.js app instrumented with
[`@launchdarkly/observability-next`](../../sdk/@launchdarkly/observability-next),
validated against the **latest Next.js** (16.x) with both the App Router and
the Pages Router.

It runs the LaunchDarkly observability and session replay plugins in
**standalone mode** — initialized directly from a LaunchDarkly SDK key, with no
feature-flag client.

## What it demonstrates

### App Router (src/app)

| Route | What it exercises |
| ----- | ----------------- |
| `src/app/layout.tsx` | `<LDObservabilityInit />` + `<ErrorBoundary />` (client init, standalone mode) |
| `src/instrumentation.ts` | `registerObservability` (server init, Node.js runtime only) |
| `src/app/page.tsx` | hub with client demo buttons: uncaught errors, error-boundary crash, `recordError`, `console.error`, `startManualSpan`, `recordCount`/`recordHistogram`, `recordLog` |
| `src/app/ssr/page.tsx` | SSR (`force-dynamic`), nested `serverFetch` to `/api/test` with cookie + session-header forwarding, `?error=1` records a server-side error |
| `src/app/isr/page.tsx` | ISR (`revalidate = 15`) |
| `src/app/ssg/page.tsx` | static prerender |
| `src/app/streaming/page.tsx` | Suspense streaming with three parallel slow fetches (span waterfall) |
| `src/app/server-action/` | Next.js server actions (success + deliberate failure) |
| `src/app/client-nav/*` | client-side navigation (session continuity) |
| `src/app/dynamic/[id]/page.tsx` | dynamic route segments |
| `src/app/danger/page.tsx` | server component render error → `app/error.tsx` boundary |
| `src/app/error.tsx` | `appRouterSsrErrorHandler` |
| `src/app/api/test/route.ts` | `AppRouterObservability` wrapper, nested self-fetch |
| `src/app/api/echo/route.ts` | POST body recording |
| `src/app/api/external/route.ts` | outbound fetch → child span |
| `src/app/api/slow/route.ts` | latency for waterfall demos |
| `src/app/api/edge/route.ts` | edge runtime: graceful degradation of the node SDK |
| `middleware.ts` | `observabilityMiddleware` forwards `sessionSecureID` → `x-highlight-request` |

### Pages Router (pages/, mounted under /legacy)

| Route | What it exercises |
| ----- | ----------------- |
| `pages/_app.tsx` | `<LDObservabilityInit />` in `_app` |
| `pages/legacy/index.tsx` | `getServerSideProps` with explicit cookie/session-header forwarding on self-fetch |
| `pages/legacy/oops.tsx` | SSR render error → custom `_error` |
| `pages/_error.tsx` | `pageRouterCustomErrorHandler` |
| `pages/api/legacy-success.ts` | `PageRouterObservability` wrapper (success) |
| `pages/api/legacy-error.ts` | `PageRouterObservability` wrapper (error) |
| `next.config.mjs` | `withLaunchDarklyConfig` (proxy rewrites + `serverExternalPackages`) |

## Run

```bash
cd e2e/nextjs-ld
cp .env.example .env
# fill NEXT_PUBLIC_LAUNCHDARKLY_CLIENT_SIDE_ID + LAUNCHDARKLY_SDK_KEY

# from the observability-sdk repo root:
yarn workspace nextjs-ld dev
# → http://localhost:3006
```

The same app validates against LaunchDarkly **staging** with:

```bash
LAUNCHDARKLY_ENV=staging yarn workspace nextjs-ld dev
```

or by setting explicit overrides in `.env`:

```bash
LAUNCHDARKLY_BACKEND_URL=https://pub.observability.ld-stg.launchdarkly.com
LAUNCHDARKLY_OTEL_ENDPOINT=https://otel.observability.ld-stg.launchdarkly.com:4318
```

## Validate

```bash
yarn workspace nextjs-ld validate          # runs scripts/validate.mjs
```

The script checks route statuses (including the deliberate errors), that the
same-origin proxy rewrites (`/v1/traces`, `/highlight-events`) forward to
LaunchDarkly rather than 404, and that the configured LaunchDarkly targets are
reachable.

### Manual UI verification (LaunchDarkly dashboard)

1. **Session replay** — browse the app in a browser (click the demo buttons,
   navigate pages). A session appears in the LaunchDarkly UI; play it back and
   confirm clicks, console output and network recordings are captured.
2. **Errors** — the thrown errors (client, boundary, SSR, route handlers)
   appear under Errors, each linked to the session (`Secure Session ID`).
3. **Traces** — under Tracing, confirm:
   - `GET - /api/test` (route wrapper spans) with `x-highlight-request`
     attribute matching the session,
   - SSR/ISR/SSG/server-action page spans with nested API spans,
   - outbound fetch spans for `/api/external`.
4. **Logs** — `console.*` from server components/route handlers and browser
   console messages appear linked to traces/session.
5. **Session↔trace correlation** — open a backend trace for an API call made
   from a recorded session; the trace should show the session id forwarded by
   middleware (`x-highlight-request`).

## Notes

- **Edge runtime**: the OTel node SDK is not edge-compatible; the SDK ships an
  edge stub (`server.edge.ts`) with clear errors. `observabilityMiddleware` and
  `middleware.ts` are edge-safe. See `src/app/api/edge/route.ts`.
- **Next.js 16**: `middleware.ts` is deprecated in favor of `proxy.ts`; the
  same code works in either file. This example keeps `middleware.ts` for
  compatibility down to Next.js 14.2 (the SDK's peer range).
- **Turbopack**: `serverExternalPackages` (configured by
  `withLaunchDarklyConfig`) keeps `@launchdarkly/observability-node`,
  `require-in-the-middle` and friends externals under Turbopack builds,
  which is required for OTel instrumentation patching to work.
