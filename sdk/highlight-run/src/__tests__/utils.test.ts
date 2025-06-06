import {
	normalizeUrl,
	shouldNetworkRequestBeRecorded,
	shouldNetworkRequestBeTraced,
} from '../client/listeners/network-listener/utils/utils'
import { internalLogOnce } from '../sdk/util'

type TestLogLevel = 'debug' | 'info' | 'error' | 'warn'

describe('normalizeUrl', () => {
	vi.stubGlobal('location', {
		origin: 'https://pub.highlight.run',
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	test.each([
		['/api/todo/create', 'https://pub.highlight.run/api/todo/create'],
		[
			'https://example.com/trailing/slash/',
			'https://example.com/trailing/slash',
		],
		['https://example.com/no/change', 'https://example.com/no/change'],
	])('normalizeUrl(%s, %s) -> %s', (url, expected) => {
		expect(normalizeUrl(url)).toBe(expected)
	})
})

describe('shouldNetworkRequestBeTraced', () => {
	beforeEach(() => {
		vi.stubGlobal('location', {
			origin: 'https://pub.highlight.run',
		})
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('records localhost when tracingOrigins is true', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://localhost/api/todo/create',
				true,
				[],
			),
		).toBe(true)
	})

	it('records relative urls when tracingOrigins is true', () => {
		expect(shouldNetworkRequestBeTraced('/api/todo/create', true, [])).toBe(
			true,
		)
	})

	it('records when tracingOrigins is true and the url matches the browser location', () => {
		window.location.host = 'example.com'

		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/todo/create',
				true,
				[],
			),
		).toBe(true)
	})

	it('does not record when tracingOrigins is true and the url does not match the browser location', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/todo/create',
				true,
				[],
			),
		).toBe(false)
	})

	it('records when tracingOrigins matches', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/todo/create',
				['example.com'],
				[],
			),
		).toBe(true)
	})

	it('does not record when domain is not in tracingOrigins', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/todo/create',
				['foo.example.com'],
				[],
			),
		).toBe(false)
	})

	it('records when tracingOrigins regex matches', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/todo/create',
				[/.*example\.com.*/],
				[],
			),
		).toBe(true)
	})

	it('records when tracingOrigins is true and urlBlocklist does not match', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/todo/create',
				['example.com'],
				['https://example.com/api/v2/todo/create'],
			),
		).toBe(true)
	})

	it('does not record when tracingOrigins is true and urlBlocklist matches', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://example.com/api/todo/create',
				true,
				['https://example.com/api'],
			),
		).toBe(false)
	})

	it('does not record highlight endpoints when tracingOrigins is empty', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://otel.highlight.io/v1/traces',
				[],
				[],
			),
		).toBe(false)
	})

	it('records highlight endpoints when domain matches tracingOrigins regex', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://otel.highlight.io/v1/traces',
				[/.*highlight\.io/],
				[],
			),
		).toBe(true)
	})

	it('records highlight endpoints when domain matches tracingOrigins', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://otel.highlight.io/v1/traces',
				['otel.highlight.io'],
				[],
			),
		).toBe(true)
	})

	it('does not record highlight endpoints when domain matches tracing origins and urlBlocklist', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://otel.highlight.io/v1/traces',
				['otel.highlight.io'],
				['otel.highlight.io'],
			),
		).toBe(false)
	})

	it('does not record the pub.highlight.io highlight endpoint by default', () => {
		expect(
			shouldNetworkRequestBeTraced(
				'https://pub.highlight.io/v1/traces',
				[],
				[],
			),
		).toBe(false)
	})
})

describe('shouldNetworkRequestBeRecorded', () => {
	vi.stubGlobal('location', {
		origin: 'https://pub.highlight.run',
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('records external URLs when tracingOrigins is true', () => {
		expect(
			shouldNetworkRequestBeRecorded(
				'https://example.com/api/todo/create',
				[],
				true,
			),
		).toBe(true)
	})

	it('records external URLs when tracingOrigins matches', () => {
		expect(
			shouldNetworkRequestBeRecorded(
				'https://example.com/api/todo/create',
				[],
				['example.com'],
			),
		).toBe(true)
	})

	it('records external URLs when tracingOrigins do not match', () => {
		expect(
			shouldNetworkRequestBeRecorded(
				'https://example.com/api/todo/create',
				[],
				['foo.example.com'],
			),
		).toBe(true)
	})

	it('records highlight endpoints when tracingOrigins is empty', () => {
		expect(
			shouldNetworkRequestBeRecorded(
				'https://otel.highlight.io/v1/traces',
				[],
				[],
			),
		).toBe(true)
	})

	it('records highlight endpoints when tracingOrigins regex matches', () => {
		expect(
			shouldNetworkRequestBeRecorded(
				'https://otel.highlight.io/v1/traces',
				[],
				[/.*highlight\.io/],
			),
		).toBe(true)
	})

	it('records highlight endpoints when tracingOrigins matches', () => {
		expect(
			shouldNetworkRequestBeRecorded(
				'https://otel.highlight.io/v1/traces',
				[],
				['otel.highlight.io'],
			),
		).toBe(true)
	})

	it('does not record highlight endpoints when passed in as an argument', () => {
		expect(
			shouldNetworkRequestBeRecorded(
				'https://otel.highlight.io/v1/traces',
				['otel.highlight.io'],
				[],
			),
		).toBe(false)
	})

	it('does not highlight endpoints even when they are passed in as an argument if in tracingOrigins', () => {
		expect(
			shouldNetworkRequestBeRecorded(
				'https://otel.highlight.io/v1/traces',
				['otel.highlight.io'],
				[/.*highlight\.io/],
			),
		).toBe(false)
	})
})

describe('internalLogOnce', () => {
	let consoleWarnSpy: any
	let consoleInfoSpy: any
	let consoleErrorSpy: any
	let consoleDebugSpy: any
	let spyByLevel: Record<TestLogLevel, any>

	beforeEach(() => {
		consoleDebugSpy = vi
			.spyOn(console, 'debug')
			.mockImplementation(() => {})
		consoleInfoSpy = vi.spyOn(console, 'info').mockImplementation(() => {})
		consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
		consoleErrorSpy = vi
			.spyOn(console, 'error')
			.mockImplementation(() => {})
		spyByLevel = {
			debug: consoleDebugSpy,
			info: consoleInfoSpy,
			warn: consoleWarnSpy,
			error: consoleErrorSpy,
		}
	})

	afterEach(() => {
		vi.restoreAllMocks()
		vi.clearAllMocks()
	})

	it('logs a message only once for the same context and logOnceId', () => {
		const context = 'test-context-1'
		const logOnceId = 'test-id'
		const message = 'test message'

		// First call should log
		internalLogOnce(context, logOnceId, 'warn', message)
		expect(consoleWarnSpy).toHaveBeenCalledTimes(1)
		expect(consoleWarnSpy).toHaveBeenCalledWith(
			'[@launchdarkly plugins]: (test-context-1): ',
			message,
		)

		// Second call with same context and logOnceId should not log
		internalLogOnce(context, logOnceId, 'warn', message)
		expect(consoleWarnSpy).toHaveBeenCalledTimes(1) // Still only called once

		// Third call with same context and logOnceId should not log
		internalLogOnce(context, logOnceId, 'warn', 'different message')
		expect(consoleWarnSpy).toHaveBeenCalledTimes(1) // Still only called once
	})

	it('allows logging the same logOnceId in different contexts', () => {
		const logOnceId = 'shared-id'
		const context1 = 'test-context-2'
		const context2 = 'test-context-3'
		const message = 'test message'

		// First call with context-1 should log
		internalLogOnce(context1, logOnceId, 'warn', message)
		expect(consoleWarnSpy).toHaveBeenCalledTimes(1)
		expect(consoleWarnSpy).toHaveBeenLastCalledWith(
			'[@launchdarkly plugins]: (test-context-2): ',
			message,
		)

		// Call with context-2 and same logOnceId should also log
		internalLogOnce(context2, logOnceId, 'warn', message)
		expect(consoleWarnSpy).toHaveBeenCalledTimes(2)
		expect(consoleWarnSpy).toHaveBeenLastCalledWith(
			'[@launchdarkly plugins]: (test-context-3): ',
			message,
		)

		// Second call with context-1 should not log
		internalLogOnce(context1, logOnceId, 'warn', message)
		expect(consoleWarnSpy).toHaveBeenCalledTimes(2) // Still only 2 calls

		// Second call with context-2 should not log
		internalLogOnce(context2, logOnceId, 'warn', message)
		expect(consoleWarnSpy).toHaveBeenCalledTimes(2) // Still only 2 calls
	})

	it.each(['debug', 'info', 'error', 'warn'])(
		'works with different log levels',
		(level) => {
			const context = 'test-context-4'
			const logOnceId = `test-id-${level}`
			const message = 'test message'

			// Test with 'info' level
			internalLogOnce(context, logOnceId, level as keyof Console, message)
			expect(spyByLevel[level as TestLogLevel]).toHaveBeenCalledTimes(1)
			expect(spyByLevel[level as TestLogLevel]).toHaveBeenCalledWith(
				'[@launchdarkly plugins]: (test-context-4): ',
				message,
			)
		},
	)

	it('handles multiple arguments correctly', () => {
		const context = 'test-context-5'
		const logOnceId = 'test-id'

		internalLogOnce(context, logOnceId, 'warn', 'message1', 'message2', {
			key: 'value',
		})
		expect(consoleWarnSpy).toHaveBeenCalledTimes(1)
		expect(consoleWarnSpy).toHaveBeenCalledWith(
			'[@launchdarkly plugins]: (test-context-5): ',
			'message1',
			'message2',
			{ key: 'value' },
		)

		// Second call should not log
		internalLogOnce(context, logOnceId, 'warn', 'different', 'args')
		expect(consoleWarnSpy).toHaveBeenCalledTimes(1) // Still only called once
	})
})
