import { describe, expect, it } from 'vitest'
import { determineMaskInputOptions } from '../client/utils/privacy'

describe('determineMaskInputOptions', () => {
	it('masks all inputs for strict regardless of overrides', () => {
		expect(determineMaskInputOptions('strict', false, { text: false })).toEqual(
			[true, undefined],
		)
	})

	it('masks all inputs for default when maskAllInputs is not set', () => {
		expect(determineMaskInputOptions('default')).toEqual([true, undefined])
		expect(determineMaskInputOptions('default', true)).toEqual([
			true,
			undefined,
		])
	})

	it('lets default opt out of masking all inputs, keeping passwords masked', () => {
		expect(determineMaskInputOptions('default', false)).toEqual([
			false,
			{ password: true },
		])
		expect(
			determineMaskInputOptions('default', false, {
				email: true,
				password: false,
			}),
		).toEqual([false, { email: true, password: true }])
	})

	it('only masks passwords for none unless configured', () => {
		expect(determineMaskInputOptions('none')).toEqual([
			false,
			{ password: true },
		])
		expect(determineMaskInputOptions('none', true, { email: true })).toEqual([
			true,
			{ email: true, password: true },
		])
	})
})
