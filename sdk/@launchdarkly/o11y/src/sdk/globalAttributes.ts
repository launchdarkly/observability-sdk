import type { Attributes, AttributeValue } from '@opentelemetry/api'
import { internalLog } from './util'

/**
 * Keys the SDK manages itself. Setting them globally would corrupt session
 * attribution (or, for `highlight.record`, drop every span), so they are
 * ignored by `setAttributes`.
 */
export const RESERVED_GLOBAL_ATTRIBUTE_KEYS: ReadonlySet<string> = new Set([
	'highlight.session_id',
	'session.id',
	'highlight.record',
])

type GlobalAttributesState = { snapshot: Readonly<Attributes> }

const EMPTY: Readonly<Attributes> = Object.freeze({})

/**
 * `@launchdarkly/observability` and `@launchdarkly/session-replay` each bundle
 * their own copy of this module while sharing one `LDObserve` facade, so the
 * attributes live on the global object and every copy on the page sees them.
 */
const GLOBAL_ATTRIBUTES_STATE_KEY = Symbol.for(
	'@launchdarkly/o11y.globalAttributes',
)

const state: GlobalAttributesState = (() => {
	const holder = globalThis as unknown as Record<
		symbol,
		GlobalAttributesState
	>
	return (holder[GLOBAL_ATTRIBUTES_STATE_KEY] ??= { snapshot: EMPTY })
})()

const isPrimitive = (v: unknown): v is string | number | boolean =>
	typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean'

const sanitizeValue = (value: unknown): AttributeValue | undefined => {
	if (isPrimitive(value)) {
		return value
	}
	if (!Array.isArray(value)) {
		return undefined
	}
	let elementType: string | undefined
	for (const element of value) {
		if (element === null || element === undefined) {
			continue
		}
		if (!isPrimitive(element)) {
			return undefined
		}
		elementType ??= typeof element
		if (typeof element !== elementType) {
			return undefined
		}
	}
	return Object.freeze(value.slice()) as AttributeValue
}

/** The current global attributes. The returned object is frozen. */
export const getGlobalAttributes = (): Readonly<Attributes> => state.snapshot

/**
 * Merges `attributes` by key into the global attributes. Reserved keys and
 * values that are not valid OpenTelemetry attribute values are ignored.
 */
export const setGlobalAttributes = (attributes: Attributes) => {
	if (!attributes || typeof attributes !== 'object') {
		return
	}
	let next: Attributes | undefined
	for (const [key, value] of Object.entries(attributes)) {
		if (!key) {
			continue
		}
		if (RESERVED_GLOBAL_ATTRIBUTE_KEYS.has(key)) {
			internalLog(
				'setAttributes',
				'debug',
				`Ignoring reserved attribute key "${key}".`,
			)
			continue
		}
		const sanitized = sanitizeValue(value)
		if (sanitized === undefined) {
			internalLog(
				'setAttributes',
				'debug',
				`Ignoring invalid value for attribute key "${key}".`,
			)
			continue
		}
		next ??= { ...state.snapshot }
		next[key] = sanitized
	}
	if (next) {
		state.snapshot = Object.freeze(next)
	}
}

/** Removes `keys` from the global attributes. */
export const removeGlobalAttributes = (keys: string[]) => {
	if (!Array.isArray(keys)) {
		return
	}
	let next: Attributes | undefined
	for (const key of keys) {
		if (typeof key === 'string' && key in (next ?? state.snapshot)) {
			next ??= { ...state.snapshot }
			delete next[key]
		}
	}
	if (next) {
		state.snapshot = Object.freeze(next)
	}
}

/** Removes every global attribute. Intended for tests. */
export const clearGlobalAttributes = () => {
	state.snapshot = EMPTY
}

/**
 * Sets each global attribute the span does not already have, so attributes
 * passed when starting the span (and any set on it later) take precedence.
 */
export const applyGlobalAttributesToSpan = (span: {
	readonly attributes: Attributes
	setAttribute(key: string, value: AttributeValue): unknown
}) => {
	const globals = state.snapshot
	for (const key in globals) {
		if (span.attributes[key] === undefined) {
			span.setAttribute(key, globals[key]!)
		}
	}
}
