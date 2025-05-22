---
'highlight.run': patch
'@launchdarkly/observability': patch
'@launchdarkly/session-replay': patch
---

fix span duplication happening due to an unnecessary export retry
