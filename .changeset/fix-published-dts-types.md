---
'highlight.run': patch
---

Fix broken TypeScript declarations in the published package. The rolled-up `dist/index.d.ts` imported types from packages that are not runtime dependencies (`@opentelemetry/api`, `@highlight-run/rrweb-types`, `graphql-request`/`graphql`, `stacktrace-js`) and from an internal path alias (`client/types/observe`). Those specifiers are unresolvable in a consumer's `node_modules`, so importing the package failed type-checking whenever `skipLibCheck` was not enabled (TypeScript's default). Now `@opentelemetry/api` and `@highlight-run/rrweb-types` are inlined into the declaration bundle, `stacktrace-js` is a runtime dependency, the internal alias import is relative, and the internal GraphQL client (which dragged `graphql-request`/`graphql` into the public types) is kept off the public surface. This also fixes the re-exported types in `@launchdarkly/observability` and `@launchdarkly/session-replay`.
