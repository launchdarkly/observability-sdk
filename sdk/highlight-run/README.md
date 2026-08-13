# Highlight

The official Javascript SDK for [Highlight](https://highlight.run).

Session replay recording is powered by the [launchdarkly/rrweb](https://github.com/launchdarkly/rrweb) fork, currently synced with upstream rrweb v2.0.1.

Recording reconciles CSS-in-JS stylesheets (emotion, styled-components) against
what it has reported every few seconds, so rules still reach the replay when
another script on the page displaces rrweb's `CSSStyleSheet.prototype.insertRule`
patch.

## Next Steps

-   [Documentation](https://docs.highlight.run)
