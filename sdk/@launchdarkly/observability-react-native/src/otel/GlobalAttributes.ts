import { Attributes, AttributeValue } from '@opentelemetry/api'
import { isAttributeValue } from '@opentelemetry/core'

// Session keys managed by the SDK itself; globals must never override them.
export const RESERVED_GLOBAL_ATTRIBUTE_KEYS: ReadonlySet<string> = new Set([
	'highlight.session_id',
	'session.id',
])

const EMPTY: Readonly<Attributes> = Object.freeze({})

/**
 * Process-wide, in-memory attributes applied to every span, log record and
 * metric data point. Readers get an immutable snapshot that is only rebuilt
 * when the attributes change, so the hot path does no copying.
 */
export class GlobalAttributes {
	private snapshot: Readonly<Attributes> = EMPTY

	/**
	 * Merges `attributes` by key. Returns the keys that were ignored because
	 * they are reserved or their value is not a valid attribute value.
	 */
	setAttributes(attributes: Attributes): string[] {
		const ignored: string[] = []
		const next: Attributes = { ...this.snapshot }
		let changed = false
		for (const [key, value] of Object.entries(attributes ?? {})) {
			if (
				RESERVED_GLOBAL_ATTRIBUTE_KEYS.has(key) ||
				value === undefined ||
				!isAttributeValue(value)
			) {
				ignored.push(key)
				continue
			}
			next[key] = Array.isArray(value)
				? (value.slice() as AttributeValue)
				: value
			changed = true
		}
		if (changed) {
			this.snapshot = Object.freeze(next)
		}
		return ignored
	}

	removeAttributes(keys: string[]): void {
		const next: Attributes = { ...this.snapshot }
		let changed = false
		for (const key of keys ?? []) {
			if (key in next) {
				delete next[key]
				changed = true
			}
		}
		if (changed) {
			this.snapshot = Object.keys(next).length
				? Object.freeze(next)
				: EMPTY
		}
	}

	getAttributes(): Readonly<Attributes> {
		return this.snapshot
	}

	_resetForTesting(): void {
		this.snapshot = EMPTY
	}
}

export const globalAttributes = new GlobalAttributes()
