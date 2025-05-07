import { LDClientMin } from '../client'
import { expect, vi } from 'vitest'
import {
	setSessionData,
	setSessionSecureID,
} from '../client/utils/sessionStorage/highlightSession'
import * as otel from '../client/otel'
import { Highlight } from '../client'
import { Observe } from '../api/observe'
import { ObserveSDK } from '../sdk/observe'

const sessionData = {
	sessionSecureID: 'foo',
	projectID: 1,
	payloadID: 1,
	lastPushTime: new Date().getTime(),
	sessionStartTime: new Date().getTime(),
}

describe('should work outside of the browser in unit test', () => {
	let highlight: Highlight
	let observe: Observe

	beforeEach(() => {
		vi.useFakeTimers()
		highlight = new Highlight({
			organizationID: '1',
			sessionSecureID: '',
			backendUrl: 'https://pub.observability.app.launchdarkly.com',
		})
		observe = new ObserveSDK({
			projectId: '1',
			sessionSecureId: '',
			otlpEndpoint:
				'https://otel.observability.app.launchdarkly.com:4318',
		})

		setSessionSecureID('foo')
		setSessionData(sessionData)
	})

	afterEach(() => {
		vi.useRealTimers()
	})

	it('should handle init', () => {
		highlight.initialize({})
	})

	it('should handle consumeError', () => {
		const error = new Error('test error')
		highlight.consumeError(error, {})
	})

	it('should handle track', () => {
		highlight.addProperties('test message', {})
	})

	it('should handle start', () => {
		highlight.initialize({ forceNew: true })
	})

	it('should handle stop', () => {
		highlight.stopRecording()
	})

	it('should handle identify', () => {
		highlight.identify('123', {})
	})

	it('should handle getSessionURL', async () => {
		setSessionData(sessionData)
		highlight.initialize()

		expect(await highlight.getCurrentSessionURL()).toBe(
			'https://app.highlight.io/1/sessions/foo',
		)
	})

	describe('startSpan', () => {
		it('it returns the value of the callback', () =>
			new Promise(async (done) => {
				let tracer: any
				await vi.waitFor(() => {
					tracer = otel.getTracer()
					expect(tracer).toBeDefined()
				})

				vi.spyOn(tracer, 'startActiveSpan')

				const value = observe.startSpan('test', () => 'test')
				expect(value).toBe('test')

				expect(tracer.startActiveSpan).toHaveBeenCalledWith(
					'test',
					expect.any(Function),
				)

				done(true)
			}))
	})

	describe('startManualSpan', () => {
		it('it returns the value of the callback', () =>
			new Promise(async (done) => {
				let tracer: any
				await vi.waitFor(() => {
					tracer = otel.getTracer()
					expect(tracer).toBeDefined()
				})

				vi.spyOn(tracer, 'startActiveSpan')

				const value = observe.startManualSpan('test', (span) => {
					span.end()
					return 'test'
				})
				expect(value).toBe('test')

				expect(tracer.startActiveSpan).toHaveBeenCalledWith(
					'test',
					expect.any(Function),
				)

				done(true)
			}))
	})
})

const sleep = (ms: number) => {
	const promise = new Promise((resolve) => setTimeout(resolve, ms))
	vi.advanceTimersByTime(ms)
	return promise
}

describe('LD integration', () => {
	let highlight: Highlight

	beforeEach(() => {
		vi.useFakeTimers()
		highlight = new Highlight({
			organizationID: '1',
			sessionSecureID: '',
		})
	})

	afterEach(() => {
		vi.useRealTimers()
	})

	it('should handle register', () => {
		const worker = (globalThis.Worker as unknown as typeof Worker).prototype
		worker.postMessage = vi.fn(
			(_message: unknown, _options?: unknown) => null,
		)

		const client: LDClientMin = {
			track: vi.fn(),
			identify: vi.fn(),
			addHook: vi.fn(),
		}
		highlight.registerLD(client)

		expect(client.addHook).not.toHaveBeenCalled()
		expect(client.identify).not.toHaveBeenCalled()
		expect(client.track).not.toHaveBeenCalled()
		expect(worker.postMessage).not.toHaveBeenCalled()

		highlight.identify('123', {})
		highlight.addProperties('test', {})
		// noop for launchdarkly
		expect(client.identify).not.toHaveBeenCalled()
		// buffered
		expect(client.track).not.toHaveBeenCalled()
		// trigger `ld client identify` whiich should call highlight hooks
		highlight._integrations[0]
			.getHooks({
				sdk: undefined,
				clientSideId: '',
			})[0]
			.afterIdentify(
				{
					context: undefined,
				},
				{},
				{
					status: 'completed',
				},
			)
		// should call buffered calls
		expect(client.track).toHaveBeenCalled()
		expect(worker.postMessage).toHaveBeenCalled()
	})
})
