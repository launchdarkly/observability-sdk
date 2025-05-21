---
'highlight.run': patch
'@launchdarkly/observability': patch
'@launchdarkly/session-replay': patch
---

wrap plugin initialization with try / catch to limit impact of internal errors
