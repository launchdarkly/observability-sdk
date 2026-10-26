import { afterEach, describe, expect, it, vi } from 'vitest'

// `@launchdarkly/observability` and `@launchdarkly/session-replay` each bundle
// their own copy of sdkLogger. Loading the module twice through a module
// registry reset reproduces that: two independent module instances on one page.
const loadSeparateCopy = async () => {
	vi.resetModules()
	return import('./sdkLogger')
}

describe('sdkLogger state shared across bundled copies', () => {
	afterEach(async () => {
		;(await import('./sdkLogger')).setSdkDebugLogging(false)
		vi.restoreAllMocks()
	})

	it('suspends console recording in every copy, not just the writer', async () => {
		const recorderCopy = await loadSeparateCopy()
		const writerCopy = await loadSeparateCopy()
		expect(writerCopy).not.toBe(recorderCopy)

		let seenByRecorder: boolean | undefined
		writerCopy.withoutConsoleRecording(() => {
			seenByRecorder = recorderCopy.isSdkConsoleOutput()
		})
		expect(seenByRecorder).toBe(true)
		expect(recorderCopy.isSdkConsoleOutput()).toBe(false)
	})

	it('marks sdkLog output from another copy as SDK output', async () => {
		const recorderCopy = await loadSeparateCopy()
		const writerCopy = await loadSeparateCopy()
		writerCopy.setSdkDebugLogging(true)

		const seen: boolean[] = []
		vi.spyOn(console, 'warn').mockImplementation(() => {
			seen.push(recorderCopy.isSdkConsoleOutput())
		})
		writerCopy.sdkLog('warn', 'from the other bundle')
		expect(seen).toEqual([true])
	})

	it('shares the debug flag across copies', async () => {
		const a = await loadSeparateCopy()
		const b = await loadSeparateCopy()
		a.setSdkDebugLogging(true)
		expect(b.isSdkDebugLogging()).toBe(true)
		b.setSdkDebugLogging(false)
		expect(a.isSdkDebugLogging()).toBe(false)
	})
})
