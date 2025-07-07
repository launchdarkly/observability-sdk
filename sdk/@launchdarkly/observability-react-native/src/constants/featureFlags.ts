import type { LDEvaluationReason } from '@launchdarkly/js-sdk-common'

export const FEATURE_FLAG_SCOPE = 'feature_flag'
export const FEATURE_FLAG_SPAN_NAME = 'evaluation'
export const FEATURE_FLAG_EVENT_NAME = `${FEATURE_FLAG_SCOPE}.${FEATURE_FLAG_SPAN_NAME}`

export const FEATURE_FLAG_KEY_ATTR = `${FEATURE_FLAG_SCOPE}.key`
export const FEATURE_FLAG_VALUE_ATTR = `${FEATURE_FLAG_SCOPE}.result.value`
export const FEATURE_FLAG_VARIATION_INDEX_ATTR = `${FEATURE_FLAG_SCOPE}.result.variationIndex`
export const FEATURE_FLAG_PROVIDER_ATTR = `${FEATURE_FLAG_SCOPE}.provider.name`
export const FEATURE_FLAG_CONTEXT_ATTR = `${FEATURE_FLAG_SCOPE}.context`
export const FEATURE_FLAG_CONTEXT_ID_ATTR = `${FEATURE_FLAG_SCOPE}.context.id`
export const FEATURE_FLAG_ENV_ATTR = `${FEATURE_FLAG_SCOPE}.environment.id`

export const LD_SCOPE = 'launchdarkly'
export const FEATURE_FLAG_APP_ID_ATTR = `${LD_SCOPE}.application.id`
export const FEATURE_FLAG_APP_VERSION_ATTR = `${LD_SCOPE}.application.version`
export const LD_IDENTIFY_RESULT_STATUS = `${LD_SCOPE}.identify.result.status`

export const FEATURE_FLAG_REASON_ATTRS: {
	[key in keyof LDEvaluationReason]: string
} = {
	kind: `${FEATURE_FLAG_SCOPE}.result.reason.kind`,
	errorKind: `${FEATURE_FLAG_SCOPE}.result.reason.errorKind`,
	ruleIndex: `${FEATURE_FLAG_SCOPE}.result.reason.ruleIndex`,
	ruleId: `${FEATURE_FLAG_SCOPE}.result.reason.ruleId`,
	prerequisiteKey: `${FEATURE_FLAG_SCOPE}.result.reason.prerequisiteKey`,
	inExperiment: `${FEATURE_FLAG_SCOPE}.result.reason.inExperiment`,
	bigSegmentsStatus: `${FEATURE_FLAG_SCOPE}.result.reason.bigSegmentsStatus`,
}

export function getCanonicalKey(context: any): string {
	if (!context) return 'anonymous'
	return context.key || context.id || 'anonymous'
}

export function getCanonicalObj(context: any): Record<string, any> {
	if (!context) return {}

	const canonical: Record<string, any> = {}

	if (context.key) canonical.key = context.key
	if (context.id) canonical.id = context.id
	if (context.name) canonical.name = context.name
	if (context.email) canonical.email = context.email
	if (context.kind) canonical.kind = context.kind

	if (context.custom && typeof context.custom === 'object') {
		Object.keys(context.custom).forEach((key) => {
			canonical[`custom.${key}`] = context.custom[key]
		})
	}

	return canonical
}
