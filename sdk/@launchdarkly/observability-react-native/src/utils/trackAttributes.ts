import type { Attributes } from '@opentelemetry/api'
import type { TrackProperties } from '../api/TrackProperties'

/**
 * Flattens a plain {@link TrackProperties} dictionary into OpenTelemetry
 * {@link Attributes}, mirroring the iOS / Android `track` converters so the same
 * payloads behave consistently across platforms.
 *
 * OTel attributes are a flat scalar map, so structure is preserved where it can be:
 *  - scalars (string / number / boolean) become scalar attributes;
 *  - homogeneous scalar arrays become array attributes;
 *  - nested objects are flattened with dot-separated keys (e.g. `user.id`);
 *  - arrays of objects (or mixed-type arrays) are flattened with indexed dotted
 *    keys (e.g. `products.0.product_id`, `products.1.price`);
 *  - `null` / `undefined` and any other value (function, symbol, etc.) are
 *    skipped — never stringified.
 */
export function flattenTrackProperties(
	properties?: TrackProperties,
): Attributes {
	const out: Attributes = {}
	if (properties) {
		putObject(out, '', properties)
	}
	return out
}

function putObject(
	out: Attributes,
	prefix: string,
	source: { [key: string]: unknown },
): void {
	for (const [rawKey, value] of Object.entries(source)) {
		const key = prefix ? `${prefix}.${rawKey}` : rawKey
		putValue(out, key, value)
	}
}

function putValue(out: Attributes, key: string, value: unknown): void {
	switch (typeof value) {
		case 'string':
		case 'boolean':
		case 'number':
			out[key] = value
			return
		case 'object':
			if (value === null) return
			if (Array.isArray(value)) {
				putArray(out, key, value)
				return
			}
			putObject(out, key, value as { [key: string]: unknown })
			return
		default:
			// undefined, function, symbol, bigint: skip; never stringify.
			return
	}
}

function putArray(out: Attributes, key: string, list: unknown[]): void {
	if (list.length === 0) return
	if (list.every((e) => typeof e === 'string')) {
		out[key] = list as string[]
	} else if (list.every((e) => typeof e === 'boolean')) {
		out[key] = list as boolean[]
	} else if (list.every((e) => typeof e === 'number')) {
		out[key] = list as number[]
	} else {
		list.forEach((element, index) => putValue(out, `${key}.${index}`, element))
	}
}
