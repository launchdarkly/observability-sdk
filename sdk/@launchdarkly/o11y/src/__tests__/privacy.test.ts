import { describe, expect, it } from 'vitest'
import {
	determineMaskInputOptions,
	determineTextPrivacySetting,
} from '../client/utils/privacy'

describe('determineTextPrivacySetting', () => {
	it('keeps strict regardless of maskPiiText', () => {
		expect(determineTextPrivacySetting('strict')).toBe('strict')
		expect(determineTextPrivacySetting('strict', false)).toBe('strict')
		expect(determineTextPrivacySetting('strict', true)).toBe('strict')
	})

	it('masks PII-like text for default unless maskPiiText is false', () => {
		expect(determineTextPrivacySetting('default')).toBe('default')
		expect(determineTextPrivacySetting('default', true)).toBe('default')
		expect(determineTextPrivacySetting('default', false)).toBe('none')
	})

	it('does not mask PII-like text for none unless maskPiiText is true', () => {
		expect(determineTextPrivacySetting('none')).toBe('none')
		expect(determineTextPrivacySetting('none', false)).toBe('none')
		expect(determineTextPrivacySetting('none', true)).toBe('default')
	})
})

describe('determineMaskInputOptions', () => {
	it('masks all inputs for strict and default', () => {
		expect(determineMaskInputOptions('strict')).toEqual([true, undefined])
		expect(determineMaskInputOptions('default', false)).toEqual([
			true,
			undefined,
		])
	})

	it('only masks passwords for none unless configured', () => {
		expect(determineMaskInputOptions('none')).toEqual([
			false,
			{ password: true },
		])
		expect(
			determineMaskInputOptions('none', false, { email: true }),
		).toEqual([false, { email: true, password: true }])
	})
})
