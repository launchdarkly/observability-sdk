import { describe, it, expect } from 'vitest'
import { flattenTrackProperties } from './trackAttributes'
import type { TrackProperties } from '../api/TrackProperties'

describe('flattenTrackProperties', () => {
	it('returns an empty object for undefined', () => {
		expect(flattenTrackProperties(undefined)).toEqual({})
	})

	it('returns an empty object for an empty payload', () => {
		// Mirrors Swift `OtelAttributesTests.emptyPayload`.
		expect(flattenTrackProperties({})).toEqual({})
	})

	it('keeps scalar values as-is', () => {
		expect(
			flattenTrackProperties({
				str: 'hello',
				num: 42,
				float: 3.14,
				yes: true,
				no: false,
			}),
		).toEqual({
			str: 'hello',
			num: 42,
			float: 3.14,
			yes: true,
			no: false,
		})
	})

	it('skips null and undefined values without stringifying', () => {
		expect(
			flattenTrackProperties({
				present: 'x',
				missing: null,
				absent: undefined,
			}),
		).toEqual({ present: 'x' })
	})

	it('drops dates and other unsupported values without stringifying', () => {
		// Mirrors Swift `OtelAttributesTests.skipsArbitraryTypes`: values with no
		// scalar/array/object attribute form (dates, functions, symbols, bigints)
		// are dropped rather than coerced to a string. A Date has no own
		// enumerable properties, so it contributes no flattened keys.
		const date = new Date(0)
		const result = flattenTrackProperties({
			keep: 1,
			date,
			fn: () => undefined,
			sym: Symbol('s'),
			big: 9n,
		} as unknown as TrackProperties)

		expect(result).toEqual({ keep: 1 })
		expect(result.date).toBeUndefined()
		expect(result.date).not.toBe(String(date))
	})

	it('preserves 64-bit integers without truncation', () => {
		// Mirrors Swift `OtelAttributesTests.preservesLong`. The value exceeds
		// Int32 range but stays within JS's safe-integer range, so it round-trips
		// without loss.
		expect(flattenTrackProperties({ id: 9_000_000_000_123 })).toEqual({
			id: 9_000_000_000_123,
		})
	})

	it('flattens nested objects with dot-separated keys', () => {
		expect(
			flattenTrackProperties({
				user: { id: '7', tier: 'gold', prefs: { theme: 'dark' } },
			}),
		).toEqual({
			'user.id': '7',
			'user.tier': 'gold',
			'user.prefs.theme': 'dark',
		})
	})

	it('keeps homogeneous scalar arrays as array attributes', () => {
		expect(
			flattenTrackProperties({
				tags: ['a', 'b'],
				flags: [true, false],
				sizes: [1, 2, 3],
			}),
		).toEqual({
			tags: ['a', 'b'],
			flags: [true, false],
			sizes: [1, 2, 3],
		})
	})

	it('flattens arrays of objects with indexed dotted keys', () => {
		expect(
			flattenTrackProperties({
				products: [
					{ product_id: 'SKU-1', quantity: 2, price: 24.0 },
					{ product_id: 'SKU-2', quantity: 1, price: 24.0 },
				],
			}),
		).toEqual({
			'products.0.product_id': 'SKU-1',
			'products.0.quantity': 2,
			'products.0.price': 24.0,
			'products.1.product_id': 'SKU-2',
			'products.1.quantity': 1,
			'products.1.price': 24.0,
		})
	})

	it('drops empty arrays', () => {
		expect(flattenTrackProperties({ empty: [] })).toEqual({})
	})

	it('converts a Segment "Product Added" flat payload', () => {
		// Mirrors Swift `OtelAttributesTests.productAdded` (analytics-taxonomy
		// §4.2): a flat e-commerce payload of scalars passes through unchanged.
		expect(
			flattenTrackProperties({
				name: 'Product Added',
				product_id: 'SKU-1234',
				quantity: 2,
				price: 24.0,
				currency: 'USD',
				cart_id: 'cart_98f1',
			}),
		).toEqual({
			name: 'Product Added',
			product_id: 'SKU-1234',
			quantity: 2,
			price: 24.0,
			currency: 'USD',
			cart_id: 'cart_98f1',
		})
	})

	it('flattens a Segment "Checkout Started" nested products payload', () => {
		// Mirrors Swift `OtelAttributesTests.checkoutStarted`. Swift nests the
		// products as an array of AttributeSets; OTel JS has no nested set type,
		// so RN flattens them into indexed dotted keys instead.
		expect(
			flattenTrackProperties({
				name: 'Checkout Started',
				order_id: 'ord_5521',
				value: 72.0,
				currency: 'USD',
				products: [
					{ product_id: 'SKU-1234', quantity: 2, price: 24.0 },
					{ product_id: 'SKU-9876', quantity: 1, price: 24.0 },
				],
			}),
		).toEqual({
			name: 'Checkout Started',
			order_id: 'ord_5521',
			value: 72.0,
			currency: 'USD',
			'products.0.product_id': 'SKU-1234',
			'products.0.quantity': 2,
			'products.0.price': 24.0,
			'products.1.product_id': 'SKU-9876',
			'products.1.quantity': 1,
			'products.1.price': 24.0,
		})
	})
})
