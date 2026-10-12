> **Note:** `highlight.run` is now published as a compatibility alias of
> [`@launchdarkly/o11y`](https://www.npmjs.com/package/@launchdarkly/o11y).
> The implementation lives in `sdk/@launchdarkly/o11y` in this repository;
> this package re-exports it unchanged. New projects should use
> [`@launchdarkly/observability`](https://www.npmjs.com/package/@launchdarkly/observability)
> and [`@launchdarkly/session-replay`](https://www.npmjs.com/package/@launchdarkly/session-replay).

# Highlight

The official Javascript SDK for [Highlight](https://highlight.run).

Session replay recording is powered by the [launchdarkly/rrweb](https://github.com/launchdarkly/rrweb) fork, currently synced with upstream rrweb v2.0.1.

Recording reconciles CSS-in-JS stylesheets (emotion, styled-components) against
what it has reported every few seconds, so rules still reach the replay when
another script on the page displaces rrweb's `CSSStyleSheet.prototype.insertRule`
patch.

## Next Steps

-   [Documentation](https://docs.highlight.run)
