---
'highlight.run': patch
'@launchdarkly/observability': patch
'@launchdarkly/session-replay': patch
---

remove verbosity of user instrumentation events by default.
only reports click, input, and submit window events as spans unless `otel.eventNames` is provided.
