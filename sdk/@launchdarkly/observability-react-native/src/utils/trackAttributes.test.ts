import { describe, it, expect } from 'vitest'
import { flattenTrackProperties } from './trackAttributes'

describe('flattenTrackProperties', () => {
	it('returns an empty object for undefined', () => {
		expect(flattenTrackProperties(undefined)).toEqual({})
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
})
