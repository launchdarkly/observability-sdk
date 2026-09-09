/**
 * `highlight.run` is published as a compatibility alias of `@launchdarkly/o11y`.
 *
 * The implementation lives in `@launchdarkly/o11y` (sdk/@launchdarkly/o11y);
 * this package re-exports its entire public surface, unchanged, so existing
 * `highlight.run` consumers keep working. New projects should depend on
 * `@launchdarkly/observability` and/or `@launchdarkly/session-replay`.
 *
 * @packageDocumentation
 */
export * from '@launchdarkly/o11y'
