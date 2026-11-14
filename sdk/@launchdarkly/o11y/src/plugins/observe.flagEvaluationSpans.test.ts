import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { Observe as ObservePlugin } from './observe'
import { ObserveSDK } from '../sdk/observe'
import { LDObserve } from '../sdk/LDObserve'
import { FEATURE_FLAG_SPAN_NAME } from '../integrations/launchdarkly'
import type { ObserveOptions } from '../client/types/observe'

const metadata = {
	sdkKey: 'test-client-side-id',
	sdk: { name: 'js-sdk', version: '1.0.0' },
}

const hookContext = {
	flagKey: 'my-flag',
	defaultValue: false,
	context: { kind: 'user', key: 'user-1' },
} as any

const detail = {
	value: true,
	variationIndex: 1,
	reason: { kind: 'FALLTHROUGH' },
} as any

function evaluate(options: ObserveOptions) {
	const plugin = new ObservePlugin(options)
	const [hook] = plugin.getHooks!(metadata)
	return hook.afterEvaluation!(hookContext, {}, detail)
}

describe('enableFlagEvaluationSpans', () => {
	let startSpan: ReturnType<typeof vi.spyOn>
	let addEvent: ReturnType<typeof vi.fn>

	beforeEach(() => {
		vi.spyOn(ObserveSDK.prototype, 'start').mockResolvedValue(undefined)
		vi.spyOn(LDObserve, 'load').mockImplementation(() => undefined)
		addEvent = vi.fn()
		startSpan = vi
			.spyOn(ObserveSDK.prototype, 'startSpan')
			.mockImplementation(((_name: string, fn: any) =>
				fn({ isRecording: () => true, addEvent })) as any)
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('emits an evaluation span by default', () => {
		evaluate({})

		expect(startSpan).toHaveBeenCalledTimes(1)
		expect(startSpan).toHaveBeenCalledWith(
			FEATURE_FLAG_SPAN_NAME,
			expect.any(Function),
		)
		expect(addEvent).toHaveBeenCalledTimes(1)
	})

	it('emits an evaluation span when explicitly enabled', () => {
		evaluate({ enableFlagEvaluationSpans: true })

		expect(startSpan).toHaveBeenCalledTimes(1)
		expect(addEvent).toHaveBeenCalledTimes(1)
	})

	it('emits no evaluation span when disabled', () => {
		const data = { some: 'data' }
		const plugin = new ObservePlugin({ enableFlagEvaluationSpans: false })
		const [hook] = plugin.getHooks!(metadata)

		const result = hook.afterEvaluation!(hookContext, data, detail)

		expect(result).toBe(data)
		expect(startSpan).not.toHaveBeenCalled()
		expect(addEvent).not.toHaveBeenCalled()
	})

	it('still runs integration evaluation hooks when disabled', () => {
		const integrationHook = { afterEvaluation: vi.fn() }
		vi.spyOn(ObserveSDK.prototype, 'getHooks').mockReturnValue([
			integrationHook as any,
		])

		evaluate({ enableFlagEvaluationSpans: false })

		expect(integrationHook.afterEvaluation).toHaveBeenCalledWith(
			hookContext,
			{},
			detail,
		)
		expect(startSpan).not.toHaveBeenCalled()
	})
})
