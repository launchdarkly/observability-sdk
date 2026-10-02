package attributes

// ProjectIDAttribute is the attribute key for the project ID.
const ProjectIDAttribute = "highlight.project_id"

// ErrorSpanName is the name of the span for errors.
const ErrorSpanName = "highlight.error"

// AttrSamplingRatio is the attribute key for the sampling ratio for sampled events and logs.
const AttrSamplingRatio = "launchdarkly.sampling.ratio"

// AttrForceSample marks a span that must be sampled regardless of the
// configured per-span-kind rate. See ldobserve.ForceSample.
const AttrForceSample = "launchdarkly.sampling.force"
