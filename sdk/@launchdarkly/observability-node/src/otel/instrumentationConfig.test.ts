import type {
	Instrumentation,
	InstrumentationConfig,
} from '@opentelemetry/instrumentation'
import { describe, expect, it, vi } from 'vitest'
import {
	applyInstrumentationConfig,
	mergeInstrumentationConfig,
	snapshotInstrumentationConfig,
} from './instrumentationConfig'

type Config = InstrumentationConfig & Record<string, any>

function fakeInstrumentation(name: string, config: Config = {}) {
	let current: Config = { enabled: true, ...config }
	return {
		instrumentationName: name,
		instrumentationVersion: '0.0.0',
		getConfig: () => current,
		setConfig: vi.fn((c: Config) => {
			current = { enabled: true, ...c }
		}),
		enable: vi.fn(),
		disable: vi.fn(),
		setTracerProvider: vi.fn(),
		setMeterProvider: vi.fn(),
	} as unknown as Instrumentation & {
		setConfig: ReturnType<typeof vi.fn>
		enable: ReturnType<typeof vi.fn>
		disable: ReturnType<typeof vi.fn>
	}
}

describe('mergeInstrumentationConfig', () => {
	it('lets later plain values replace earlier ones', () => {
		const merged = mergeInstrumentationConfig(
			{ a: 1, b: 1 } as Config,
			{ b: 2 } as Config,
		) as Config
		expect(merged).toEqual({ a: 1, b: 2 })
	})

	it('chains hooks set on both sides, earlier first, later return value', () => {
		const calls: string[] = []
		const merged = mergeInstrumentationConfig(
			{ hook: () => (calls.push('sdk'), 'sdk') } as Config,
			{ hook: () => (calls.push('user'), 'user') } as Config,
		) as Config
		expect(merged.hook('x')).toBe('user')
		expect(calls).toEqual(['sdk', 'user'])
	})

	it('passes the same arguments to both hooks', () => {
		const first = vi.fn()
		const second = vi.fn()
		const merged = mergeInstrumentationConfig(
			{ hook: first } as Config,
			{ hook: second } as Config,
		) as Config
		merged.hook(1, 'two')
		expect(first).toHaveBeenCalledWith(1, 'two')
		expect(second).toHaveBeenCalledWith(1, 'two')
	})

	it('still runs the later hook when the earlier one throws', () => {
		const second = vi.fn(() => true)
		const merged = mergeInstrumentationConfig(
			{
				hook: () => {
					throw new Error('boom')
				},
			} as Config,
			{ hook: second } as Config,
		) as Config
		expect(merged.hook()).toBe(true)
		expect(second).toHaveBeenCalled()
	})

	it('uses a hook as is when only one side sets it', () => {
		const hook = () => true
		const merged = mergeInstrumentationConfig({}, {
			hook,
		} as Config) as Config
		expect(merged.hook).toBe(hook)
	})
})

describe('applyInstrumentationConfig', () => {
	it('applies user config over the load-time config', () => {
		const loadHook = vi.fn()
		const http = fakeInstrumentation('http', { logHook: loadHook, x: 1 })
		const snapshot = snapshotInstrumentationConfig([http])
		const ignore = () => true

		applyInstrumentationConfig(
			[http],
			snapshot,
			{},
			{ http: { ignoreIncomingRequestHook: ignore } as Config },
		)

		const config = http.getConfig() as Config
		expect(config.ignoreIncomingRequestHook).toBe(ignore)
		expect(config.logHook).toBe(loadHook)
		expect(config.x).toBe(1)
	})

	it('runs SDK hooks before user hooks', () => {
		const calls: string[] = []
		const pg = fakeInstrumentation('pg')
		applyInstrumentationConfig(
			[pg],
			snapshotInstrumentationConfig([pg]),
			{ pg: { responseHook: () => calls.push('sdk') } as Config },
			{ pg: { responseHook: () => calls.push('user') } as Config },
		)
		;(pg.getConfig() as Config).responseHook()
		expect(calls).toEqual(['sdk', 'user'])
	})

	it('merges from the snapshot, so repeated applies do not stack hooks', () => {
		const user = vi.fn()
		const pino = fakeInstrumentation('pino', { logHook: vi.fn() })
		const snapshot = snapshotInstrumentationConfig([pino])
		const config = { pino: { logHook: user } as Config }

		applyInstrumentationConfig([pino], snapshot, {}, config)
		applyInstrumentationConfig([pino], snapshot, {}, config)
		;(pino.getConfig() as Config).logHook()

		expect(user).toHaveBeenCalledTimes(1)
	})

	it('restores the load-time config when no config is given', () => {
		const http = fakeInstrumentation('http', { x: 1 })
		const snapshot = snapshotInstrumentationConfig([http])
		applyInstrumentationConfig(
			[http],
			snapshot,
			{},
			{
				http: { x: 2 } as Config,
			},
		)
		applyInstrumentationConfig([http], snapshot, {})
		expect((http.getConfig() as Config).x).toBe(1)
	})

	it('disables an instrumentation set to enabled: false', () => {
		const http = fakeInstrumentation('http')
		applyInstrumentationConfig(
			[http],
			snapshotInstrumentationConfig([http]),
			{},
			{ http: { enabled: false } },
		)
		expect(http.disable).toHaveBeenCalled()
		expect(http.enable).not.toHaveBeenCalled()
	})

	it('returns names that match no registered instrumentation', () => {
		const http = fakeInstrumentation('http')
		const unknown = applyInstrumentationConfig(
			[http],
			snapshotInstrumentationConfig([http]),
			{},
			{ http: {}, missing: {} },
		)
		expect(unknown).toEqual(['missing'])
	})
})
