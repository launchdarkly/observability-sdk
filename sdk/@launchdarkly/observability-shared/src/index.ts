export { RECORD_ATTRIBUTE } from './instrumentation/constants'
export { CustomTraceContextPropagator } from './instrumentation/propagator'
export type { TracingOrigins } from './instrumentation/types'
export { getCorsUrlsPattern } from './instrumentation/utils'
export { parseGraphQLOperation } from './instrumentation/graphql'
export type {
	GraphQLOperation,
	GraphQLOperationType,
} from './instrumentation/graphql'
export {
	ExposureDeduper,
	DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS,
	DEFAULT_FLAG_EXPOSURE_DEDUPE_MAX_SIZE,
} from './launchdarkly/ExposureDeduper'
export { CustomSampler } from './sampling/CustomSampler'
export type { ExportSampler } from './sampling/ExportSampler'
export type { SamplingResult } from './sampling/ExportSampler'
export { sampleSpans } from './sampling/sampleSpans'
export { sampleLogs } from './sampling/sampleLogs'
export { getSamplingConfig } from './sampling/getSamplingConfig'
