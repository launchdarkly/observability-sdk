import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ObserveSDK } from './observe'
import * as otel from '../client/otel'
import { isSdkDebugLogging, setSdkDebugLogging } from '../client/utils/sdkLogger'

// base64url of a context JSON; not a real person.
const B64 =
	'eyJraW5kIjoidXNlciIsImtleSI6InUxIiwibmFtZSI6IkphbmUiLCJlbWFpbCI6ImphbmVAZXhhbXBsZS5jb20ifQ'

const baseOptions = {
	backendUrl: 'https://pub.highlight.io',
	otel: { otlpEndpoint: 'https://otel.highlight.io' },
	projectId: '1',
	sessionSecureId: 'test-session',
	environment: 'test',
}

describe('ObserveSDK log hygiene', () => {
	let addEvent: ReturnType<typeof vi.fn>
	let startActiveSpan: ReturnType<typeof vi.fn>

	beforeEach(() => {
		addEvent = vi.fn()
		const fakeSpan = {
			addEvent,
			setAttribute: vi.fn(),
			setAttributes: vi.fn(),
			setStatus: vi.fn(),
			recordException: vi.fn(),
			end: vi.fn(),
			isRecording: () => true,
		}
		startActiveSpan = vi.fn((_name: string, cb: (span: any) => any) =>
			cb(fakeSpan),
		)
		vi.spyOn(otel, 'getTracer').mockReturnValue({
			startActiveSpan,
		} as any)
	})

	afterEach(() => {
		setSdkDebugLogging(false)
		vi.restoreAllMocks()
	})

	it('redacts the LaunchDarkly context segment from recorded log messages', () => {
		const observe = new ObserveSDK(baseOptions)
		observe.recordLog(
			`[LaunchDarkly] Opening stream connection to https://clientstream.launchdarkly.com/eval/abc123/${B64}?h=x`,
			'info',
		)

		expect(startActiveSpan).toHaveBeenCalledWith(
			otel.LOG_SPAN_NAME,
			expect.any(Function),
		)
		expect(addEvent).toHaveBeenCalledTimes(1)
		const [eventName, attributes] = addEvent.mock.calls[0]
		expect(eventName).toBe('log')
		expect(attributes[otel.ATTR_LOG_MESSAGE]).toBe(
			'[LaunchDarkly] Opening stream connection to https://clientstream.launchdarkly.com/eval/abc123/REDACTED?h=x',
		)
		expect(attributes[otel.ATTR_LOG_SEVERITY]).toBe('info')
	})

	it('leaves other log messages untouched', () => {
		const observe = new ObserveSDK(baseOptions)
		observe.recordLog('checkout complete', 'info', { orderId: 'o-1' })

		const [, attributes] = addEvent.mock.calls[0]
		expect(attributes[otel.ATTR_LOG_MESSAGE]).toBe('checkout complete')
		expect(attributes.orderId).toBe('o-1')
	})

	it('turns on SDK-internal console diagnostics only when debug is set', () => {
		new ObserveSDK(baseOptions)
		expect(isSdkDebugLogging()).toBe(false)

		new ObserveSDK({ ...baseOptions, debug: true })
		expect(isSdkDebugLogging()).toBe(true)
	})
})
