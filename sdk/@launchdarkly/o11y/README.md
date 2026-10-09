# @launchdarkly/o11y

Shared browser foundation for LaunchDarkly Observability and Session Replay.

This package holds the implementation that powers:

-   [`@launchdarkly/observability`](https://www.npmjs.com/package/@launchdarkly/observability) — frontend errors, logs, traces, and metrics
-   [`@launchdarkly/session-replay`](https://www.npmjs.com/package/@launchdarkly/session-replay) — session replay recording
-   [`highlight.run`](https://www.npmjs.com/package/highlight.run) — compatibility alias for existing highlight.io users

Most applications should install `@launchdarkly/observability` and/or
`@launchdarkly/session-replay` rather than depending on this package directly.
See the [SDK reference](https://docs.launchdarkly.com/sdk/observability).

Session replay recording is powered by the
[launchdarkly/rrweb](https://github.com/launchdarkly/rrweb) fork.
