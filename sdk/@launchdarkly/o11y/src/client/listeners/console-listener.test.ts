import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConsoleListener, isLaunchDarklySdkLogLine } from './console-listener'
import type { ConsoleMessage } from '../types/shared-types'
import { sdkLog, setSdkDebugLogging } from '../utils/sdkLogger'

// A base64url-encoded LD context, as the JS client SDK puts it in its
// streaming and polling URLs. The payload is not a real person.
const CONTEXT_B64 =
	'eyJraW5kIjoidXNlciIsImtleSI6InUxIiwibmFtZSI6IkphbmUiLCJlbWFpbCI6ImphbmVAZXhhbXBsZS5jb20ifQ'
const STREAM_URL = `https://clientstream.launchdarkly.com/eval/6ac3ffaf1d5ff3065c65987b/${CONTEXT_B64}?h=deadbeef&withReasons=true`

const stringifyOptions = {
	depthOfLimit: 10,
	numOfKeysLimit: 100,
	stringLengthLimit: 1000,
}

const makeFakeConsole = () => ({
	log: vi.fn(),
	info: vi.fn(),
	warn: vi.fn(),
	error: vi.fn(),
})

describe('ConsoleListener and the LaunchDarkly SDK logger', () => {
	let cleanup: (() => void) | undefined
	afterEach(() => {
		cleanup?.()
		cleanup = undefined
		setSdkDebugLogging(false)
		vi.restoreAllMocks()
	})

	it('recognises lines written by the LaunchDarkly SDK logger', () => {
		expect(
			isLaunchDarklySdkLogLine([
				`[LaunchDarkly] Opening stream connection to ${STREAM_URL}`,
			]),
		).toBe(true)
		expect(
			isLaunchDarklySdkLogLine([
				'[LaunchDarkly] Closing stream connection',
			]),
		).toBe(true)
		expect(isLaunchDarklySdkLogLine(['checkout failed', { id: 1 }])).toBe(
			false,
		)
		expect(isLaunchDarklySdkLogLine([{ msg: '[LaunchDarkly]' }])).toBe(
			false,
		)
		expect(isLaunchDarklySdkLogLine([])).toBe(false)
		// js-core (JS client SDK 4.x) BasicLogger format: `<level>: [name] ...`
		expect(
			isLaunchDarklySdkLogLine([
				`info: [LaunchDarkly] Opening stream connection to ${STREAM_URL}`,
			]),
		).toBe(true)
		expect(
			isLaunchDarklySdkLogLine([
				'warn: [LaunchDarkly] Stream connection error, retrying',
			]),
		).toBe(true)
		expect(
			isLaunchDarklySdkLogLine(['error: [MyApp] something broke']),
		).toBe(false)
	})

	it('does not record the LaunchDarkly SDK logger output by default', () => {
		const fakeConsole = makeFakeConsole()
		// The listener replaces the methods, so keep the originals to assert on.
		const { info: originalInfo, warn: originalWarn } = fakeConsole
		const recorded: ConsoleMessage[] = []
		cleanup = ConsoleListener((c) => recorded.push(c), {
			level: ['log', 'info', 'warn', 'error'],
			logger: fakeConsole,
			stringifyOptions,
		})

		fakeConsole.info(
			`[LaunchDarkly] Opening stream connection to ${STREAM_URL}`,
		)
		fakeConsole.info('[LaunchDarkly] Closing stream connection')
		fakeConsole.warn(
			'[LaunchDarkly] Error on stream connection, will retry',
		)

		// The application still sees the lines on its console...
		expect(originalInfo).toHaveBeenCalledTimes(2)
		expect(originalWarn).toHaveBeenCalledTimes(1)
		// ...but none of them becomes a log record.
		expect(recorded).toHaveLength(0)

		// Ordinary application output is unaffected.
		fakeConsole.log('cart loaded', { items: 3 })
		expect(recorded).toHaveLength(1)
		expect(recorded[0].type).toBe('log')
		expect(recorded[0].value).toEqual(['cart loaded'])
	})

	it('records the LaunchDarkly SDK logger output when asked to, with the context redacted', () => {
		const fakeConsole = makeFakeConsole()
		const recorded: ConsoleMessage[] = []
		cleanup = ConsoleListener((c) => recorded.push(c), {
			level: ['info'],
			logger: fakeConsole,
			stringifyOptions,
			recordLaunchDarklySdkLogs: true,
		})

		fakeConsole.info(
			`[LaunchDarkly] Opening stream connection to ${STREAM_URL}`,
		)

		expect(recorded).toHaveLength(1)
		const line = recorded[0].value?.join(' ') ?? ''
		expect(line).toContain('[LaunchDarkly] Opening stream connection to')
		expect(line).toContain(
			'/eval/6ac3ffaf1d5ff3065c65987b/REDACTED?h=deadbeef',
		)
		expect(line).not.toContain(CONTEXT_B64)
	})

	it('redacts the LaunchDarkly context segment in any recorded line or attribute', () => {
		const fakeConsole = makeFakeConsole()
		const recorded: ConsoleMessage[] = []
		cleanup = ConsoleListener((c) => recorded.push(c), {
			level: ['error'],
			logger: fakeConsole,
			stringifyOptions,
		})

		fakeConsole.error(
			`request failed: https://clientsdk.launchdarkly.com/sdk/evalx/6ac3ffaf1d5ff3065c65987b/contexts/${CONTEXT_B64}`,
			{ url: STREAM_URL },
		)

		expect(recorded).toHaveLength(1)
		expect(recorded[0].value?.[0]).toBe(
			'request failed: https://clientsdk.launchdarkly.com/sdk/evalx/6ac3ffaf1d5ff3065c65987b/contexts/REDACTED',
		)
		expect(recorded[0].attributes).toContain(
			'/eval/6ac3ffaf1d5ff3065c65987b/REDACTED',
		)
		expect(JSON.stringify(recorded[0])).not.toContain(CONTEXT_B64)
	})

	it("never records the SDK's own diagnostics, even in debug mode", () => {
		// Spy first so the listener wraps the spy, not the real console.
		const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
		const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
		const recorded: ConsoleMessage[] = []
		cleanup = ConsoleListener((c) => recorded.push(c), {
			level: ['error', 'warn'],
			logger: console,
			stringifyOptions,
		})

		setSdkDebugLogging(true)
		sdkLog(
			'error',
			'[@launchdarkly/observability] failed to export OTeL metrics: XHR request errored',
			new Error('XHR request errored'),
		)
		sdkLog('warn', 'OTEL already initialized. Skipping...')

		// Debug mode: the diagnostics reach the developer console...
		expect(errorSpy).toHaveBeenCalledTimes(1)
		expect(warnSpy).toHaveBeenCalledTimes(1)
		// ...but are not turned into the customer's log records.
		expect(recorded).toHaveLength(0)

		// The application's own console.error still is.
		console.error('payment declined')
		expect(recorded).toHaveLength(1)
		expect(recorded[0].value).toEqual(['payment declined'])
	})

	it("keeps the SDK's own diagnostics off the console outside debug mode", () => {
		const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
		setSdkDebugLogging(false)
		sdkLog('error', '[@launchdarkly/observability] failed to export')
		expect(errorSpy).not.toHaveBeenCalled()
	})
})
