# CLAUDE.md

## Overview

LaunchDarkly Observability SDK monorepo. Contains browser, Node.js, mobile, and backend SDKs for session replay, error monitoring, logging, and distributed tracing. Fork of highlight.io.

**Package manager:** Yarn 4.9.1 (`yarn install`)
**Build system:** Turborepo
**rrweb:** Git submodule at `rrweb/` - run `git submodule update --init --recursive` after clone (the preinstall hook handles this automatically).

## Key Commands

```bash
yarn install              # Install all dependencies
yarn build                # Build all packages (excludes rrweb, nextjs)
yarn build:sdk            # Build only @launchdarkly/* + highlight.run
yarn test                 # Build, lint, enforce-size, then test all packages
yarn format-check         # Check formatting (prettier)
yarn format:all           # Fix formatting
yarn lint                 # Lint all packages
yarn enforce-size         # Check bundle size limits (256KB brotli)
yarn docs                 # Generate TypeDoc documentation
```

### Filtering to specific packages

```bash
yarn turbo run build --filter @launchdarkly/observability
yarn turbo run test --filter highlight.run
yarn turbo run build --filter '@launchdarkly/*'
```

### Publishing (CI-only, runs on main)

```bash
yarn publish              # @launchdarkly/* packages
yarn publish:highlight    # highlight.run + @highlight-run/* packages
```

## Package Structure

### LaunchDarkly SDKs (`sdk/@launchdarkly/`)

| Package | Description |
|---------|-------------|
| `observability` | Browser SDK - errors, logs, traces |
| `session-replay` | Browser SDK - session replay (thin wrapper over highlight.run) |
| `observability-shared` | Internal shared types and GraphQL codegen (private) |
| `observability-node` | Node.js SDK |
| `observability-react-native` | React Native SDK |
| `react-native-ld-session-replay` | React Native session replay |
| `observability-python` | Python SDK |
| `observability-dotnet` | .NET SDK |
| `observability-android` | Android SDK |
| `observability-java` | Java SDK |

### Core packages

| Package | Description |
|---------|-------------|
| `sdk/highlight-run` | Core browser library - session replay + observability. Most logic lives here. |
| `rrweb/` | Forked session replay recording library (git submodule, ~15 sub-packages) |
| `go/` | Go observability SDK |

### Key source files in highlight.run

- `src/index.tsx` - Public API, `H` global singleton
- `src/sdk/record.ts` - `RecordSDK` class (session replay recording logic)
- `src/sdk/LDRecord.ts` - `LDRecord` global singleton (LaunchDarkly entry point)
- `src/client/index.tsx` - `Highlight` class (underlying implementation)
- `src/client/workers/highlight-client-worker.ts` - Web Worker for async data upload
- `src/client/types/record.ts` - `RecordOptions` type definition
- `src/client/types/iframe.ts` - Cross-origin iframe protocol types
- `src/client/constants/sessions.ts` - Timing constants (`FIRST_SEND_FREQUENCY`, etc.)
- `src/plugins/record.ts` - LaunchDarkly plugin interface (`LDPlugin`)

### Default backend URL

The SDK sends data to `https://pub.observability.app.launchdarkly.com` by default. Configurable via `backendUrl` option.

## Code Style

**Prettier config** (`.prettierrc`): Tabs, no semicolons, single quotes, trailing commas, 80 char width.

**Pre-commit hook** (husky): Runs `pretty-quick --staged` automatically.

**TypeScript**: Strict mode. All browser SDKs build with Vite (ESM + UMD). Node SDKs use rollup or tsc.

**Bundle size**: All browser packages enforce 256KB brotli limit via `size-limit`. Check with `yarn enforce-size`.

## CI Pipeline

The main workflow is `.github/workflows/turbo.yml` (runs on push to main and PRs):
1. `yarn install`
2. `yarn dedupe --check`
3. `yarn format-check`
4. `yarn test` (which runs build -> lint -> enforce-size -> test)
5. On main: publishes to npm

Other workflows handle language-specific SDKs (Go, Python, .NET, Android, Java, Ruby, Rust, Elixir) and E2E tests.

Releases are managed by `release-please` - version bumps happen automatically via PR.

## E2E Tests

### Framework examples (`e2e/`)

Example apps for testing SDK integrations: React, Next.js, Angular, Express, NestJS, Remix, Hono, Cloudflare Worker, React Native, Go, Python, .NET, Ruby, and more.

```bash
# Run via docker compose
cd e2e
docker compose build sdk && docker compose build base
docker compose build <example> && docker compose up <example>

# Run specific frameworks via turbo
yarn e2e:nextjs
yarn e2e:express
yarn e2e:cloudflare
```

### Pytest E2E (`e2e/tests/`)

Python-based tests that start apps, make requests, and validate data appears in the backend via GraphQL queries. Uses `app_runner.py` for lifecycle management with health check polling.

```bash
cd e2e/tests
poetry install
poetry run python src/app_runner.py <example>
poetry run pytest
```

**Run tests:**
```bash
# Diagnose live customer URLs (headed browser, slow-mo for observation)
yarn test:live

# Run local reproduction tests (headless, uses local Express servers on :3001/:3002)
yarn test:local

# Run everything
yarn test
```

**Projects:**
- `live-debug` - Tests against live URLs. Captures SDK presence (`H`, `__HIGHLIGHT__`, `LDRecord` globals), postMessage flow, CSP headers, iframe sandbox attributes, and backend network traffic. Runs headed with slowMo.
- `local-repro` - Tests against two local Express servers simulating cross-origin parent/iframe. Tests happy path, missing SDK, `recordCrossOriginIframe: false`, delayed iframe insertion, sandbox restrictions.

**Key test files:**
- `tests/lowes-debug.spec.ts` - Full diagnostic suite against live URLs
- `tests/prove-stuck-iframe.spec.ts` - Proves root cause by sending a fake `"iframe parent ready"` postMessage and watching the iframe SDK wake up
- `tests/cross-origin-iframe.spec.ts` - Controlled local reproduction tests

**How cross-origin iframe recording works:**
1. Parent page SDK (with `recordCrossOriginIframe: true`) polls all `<iframe>` elements every 1s, sending `postMessage({ highlight: "iframe parent ready", projectID, sessionSecureID })`
2. Iframe SDK detects it's cross-origin (via `window.parent.document` throwing), waits for the parent message
3. Iframe receives project ID + session secure ID from parent, replies `{ highlight: "iframe ok" }`, then starts recording
4. **Both parent and iframe must have the SDK installed with `recordCrossOriginIframe: true`**

**Common failure modes:** SDK missing on parent page, `recordCrossOriginIframe` not enabled, iframe `sandbox` attribute missing `allow-scripts` or `allow-same-origin`.

**Helpers** (`helpers/diagnostics.ts`):
- `injectPostMessageSpy(page)` - Captures all postMessage events via `addInitScript`
- `checkSDKPresence(page|frame)` - Checks for SDK globals + extracts config, state, session ID
- `interceptBackendRequests(page)` - Route-intercepts requests to `pub.observability.app.launchdarkly.com`
- `getIframeAttributes(page)` - Returns sandbox, allow, src for all iframes
- `captureSecurityHeaders(response)` - Extracts CSP, X-Frame-Options, etc.

**Local server fixtures** (`fixtures/`): HTML pages with various SDK configurations (happy path, no SDK, disabled cross-origin, delayed iframe, sandboxed iframe).
