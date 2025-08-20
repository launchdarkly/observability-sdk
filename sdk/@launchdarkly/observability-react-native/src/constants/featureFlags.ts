import type {
	LDEvaluationReason,
	LDMultiKindContext,
	LDContext,
} from '@launchdarkly/js-sdk-common'

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

function encodeKey(key: string): string {
	if (key.includes('%') || key.includes(':')) {
		return key.replace(/%/g, '%25').replace(/:/g, '%3A')
	}
	return key
}

function isMultiContext(context: any): context is LDMultiKindContext {
	return context.kind === 'multi'
}

/**
 * Get a canonical key for a given context. The canonical key contains an encoded version of the context
 * keys.
 *
 * This format should be stable and consistent. It isn't for presentation only purposes.
 * It allows linking to a context instance.
 * @param context The context to get a canonical key for.
 * @returns The canonical context key.
 */
export function getCanonicalKey(context: LDContext) {
	if (isMultiContext(context)) {
		return Object.keys(context)
			.sort()
			.filter((key) => key !== 'kind')
			.map((key) => {
				return `${key}:${encodeKey(context[key].key)}`
			})
			.join(':')
	} else if (context.kind === 'user') {
		// If the kind is a user, then the key is directly the user key.
		return context.key
	}

	return `${context.kind}:${encodeKey(context.key)}`
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
