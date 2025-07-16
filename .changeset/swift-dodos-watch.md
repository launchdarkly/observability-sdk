---
'highlight.run': patch
'@launchdarkly/observability': patch
'@launchdarkly/session-replay': patch
---

delete sessionData\_ localstorage values to avoid overfilling quota
