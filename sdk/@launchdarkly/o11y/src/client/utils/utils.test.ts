import { isMetricSafeNumber } from './utils'

describe('isMetricSafeNumber', () => {
	describe('valid numbers', () => {
		const validNumbers = [
			{ value: 42, description: 'positive integer' },
			{ value: 0, description: 'zero' },
			{ value: 1, description: 'one' },
			{ value: -42, description: 'negative integer' },
			{ value: -1, description: 'negative one' },
			{ value: 3.14, description: 'positive float' },
			{ value: 0.5, description: 'positive decimal' },
			{ value: 1.0, description: 'float one' },
			{ value: -3.14, description: 'negative float' },
			{ value: -0.5, description: 'negative decimal' },
			{ value: Number.MIN_VALUE, description: 'MIN_VALUE' },
			{ value: Number.EPSILON, description: 'EPSILON' },
			{ value: Number.MAX_SAFE_INTEGER, description: 'MAX_SAFE_INTEGER' },
			{ value: Number.MAX_VALUE, description: 'MAX_VALUE' },
		]

		test.each(validNumbers)(
			'should return true for $description ($value)',
			({ value }) => {
				expect(isMetricSafeNumber(value)).toBe(true)
			},
		)
	})

	describe('invalid numbers', () => {
		const invalidNumbers = [
			{ value: NaN, description: 'NaN' },
			{ value: Number.NaN, description: 'Number.NaN' },
			{ value: Infinity, description: 'Infinity' },
			{
				value: Number.POSITIVE_INFINITY,
				description: 'Number.POSITIVE_INFINITY',
			},
			{ value: -Infinity, description: 'negative Infinity' },
			{
				value: Number.NEGATIVE_INFINITY,
				description: 'Number.NEGATIVE_INFINITY',
			},
		]

		test.each(invalidNumbers)(
			'should return false for $description',
			({ value }) => {
				expect(isMetricSafeNumber(value)).toBe(false)
			},
		)
	})

	describe('non-number types', () => {
		const nonNumbers = [
			{ value: '42', description: 'numeric string' },
			{ value: '3.14', description: 'decimal string' },
			{ value: 'not a number', description: 'text string' },
			{ value: '', description: 'empty string' },
			{ value: true, description: 'boolean true' },
			{ value: false, description: 'boolean false' },
			{ value: {}, description: 'empty object' },
			{ value: { value: 42 }, description: 'object with properties' },
			{ value: [], description: 'empty array' },
			{ value: [1, 2, 3], description: 'array with numbers' },
			{ value: null, description: 'null' },
			{ value: undefined, description: 'undefined' },
			{ value: () => {}, description: 'arrow function' },
			{ value: function () {}, description: 'function declaration' },
			{ value: Symbol('test'), description: 'symbol' },
			{ value: BigInt(42), description: 'BigInt constructor' },
			{ value: 42n, description: 'BigInt literal' },
			{ value: new Date(), description: 'Date object' },
			{ value: new Date('2023-01-01'), description: 'Date with string' },
			{ value: /test/, description: 'RegExp literal' },
			{ value: new RegExp('test'), description: 'RegExp constructor' },
		]

		test.each(nonNumbers)(
			'should return false for $description',
			({ value }) => {
				expect(isMetricSafeNumber(value)).toBe(false)
			},
		)
	})
})
