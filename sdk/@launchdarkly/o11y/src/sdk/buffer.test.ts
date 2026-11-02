import { afterEach, describe, expect, it, vi } from 'vitest'

import { BufferedClass } from './buffer'
import { LDObserve } from './LDObserve'
import { LDRecord } from './LDRecord'
import { Observe as ObservePlugin } from '../plugins/observe'
import { ObserveSDK } from './observe'
import { setSdkDebugLogging } from '../client/utils/sdkLogger'

// The `LDObserve` / `LDRecord` singletons are shared through `globalThis`, so
// tests build their own instances (via the instance constructor) to avoid
// ordering effects between test files sharing a worker.
function freshObserve(): typeof LDObserve {
	return new (
		LDObserve as unknown as {
			constructor: new () => typeof LDObserve
		}
	).constructor()
}

function freshRecord(): typeof LDRecord {
	return new (
		LDRecord as unknown as {
			constructor: new () => typeof LDRecord
		}
	).constructor()
}

interface FakeObserveSDK {
	start: ReturnType<typeof vi.fn>
	stop: ReturnType<typeof vi.fn>
	recordLog: ReturnType<typeof vi.fn>
}

function fakeObserveSDK(): FakeObserveSDK {
	return {
		start: vi.fn(),
		stop: vi.fn(),
		recordLog: vi.fn(),
	}
}

describe('BufferedClass', () => {
	afterEach(() => {
		setSdkDebugLogging(false)
		vi.restoreAllMocks()
	})

	it('buffers calls until the SDK is marked ready, then replays in order', () => {
		class Testable extends BufferedClass<FakeObserveSDK> {
			public replay = () => this.markReady()
			public call = (method: string, args: any[]) =>
				this._bufferCall(method, args)
			public set capacity(value: number) {
				this._capacity = value
			}
			public get queued() {
				return this._callBuffer.length
			}
		}

		const sdk = fakeObserveSDK()
		const buffered = new Testable()
		buffered.load(sdk, { ready: false })

		buffered.call('recordLog', ['a'])
		buffered.call('recordLog', ['b'])
		expect(sdk.recordLog).not.toHaveBeenCalled()

		buffered.replay()
		expect(sdk.recordLog).toHaveBeenNthCalledWith(1, 'a')
		expect(sdk.recordLog).toHaveBeenNthCalledWith(2, 'b')
		expect(buffered.queued).toBe(0)

		// After being ready, calls run directly.
		buffered.call('recordLog', ['c'])
		expect(sdk.recordLog).toHaveBeenLastCalledWith('c')
	})

	it('caps the buffer and warns once when capacity is exceeded', () => {
		class Testable extends BufferedClass<FakeObserveSDK> {
			public call = (method: string, args: any[]) =>
				this._bufferCall(method, args)
			public set capacity(value: number) {
				this._capacity = value
			}
			public get queued() {
				return this._callBuffer.length
			}
		}

		setSdkDebugLogging(true)
		const warnSpy = vi.spyOn(console, 'warn')
		const sdk = fakeObserveSDK()
		const buffered = new Testable()
		buffered.capacity = 2
		buffered.load(sdk, { ready: false })

		buffered.call('recordLog', ['a'])
		buffered.call('recordLog', ['b'])
		buffered.call('recordLog', ['c'])
		buffered.call('recordLog', ['d'])

		// Calls past the capacity are dropped.
		expect(buffered.queued).toBe(2)
		expect(warnSpy).toHaveBeenCalledTimes(1)
	})
})

describe('LDObserve with manualStart', () => {
	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('delivers telemetry recorded before start(), in order, once start() runs', async () => {
		const sdk = fakeObserveSDK()
		const observe = freshObserve()

		observe.load(sdk as unknown as ObserveSDK, { ready: false })

		observe.recordLog('A: before start', 'info')
		await observe.start()
		observe.recordLog('B: after start', 'info')

		expect(sdk.start).toHaveBeenCalledTimes(1)
		expect(sdk.recordLog).toHaveBeenNthCalledWith(
			1,
			'A: before start',
			'info',
			undefined,
		)
		expect(sdk.recordLog).toHaveBeenNthCalledWith(
			2,
			'B: after start',
			'info',
			undefined,
		)
	})

	it('does not drop a start() made before the plugin registers', async () => {
		const sdk = fakeObserveSDK()
		const observe = freshObserve()

		// start() before any plugin registration must not throw.
		const started = observe.start()
		observe.recordLog('A: before registration', 'info')

		observe.load(sdk as unknown as ObserveSDK, { ready: false })
		await started
		observe.recordLog('B: after start', 'info')

		expect(sdk.start).toHaveBeenCalled()
		expect(sdk.recordLog).toHaveBeenNthCalledWith(
			1,
			'A: before registration',
			'info',
			undefined,
		)
		expect(sdk.recordLog).toHaveBeenNthCalledWith(
			2,
			'B: after start',
			'info',
			undefined,
		)
	})

	it('resolves concurrent start() calls with a single sdk start', async () => {
		const sdk = fakeObserveSDK()
		const observe = freshObserve()

		const first = observe.start()
		const second = observe.start()
		observe.load(sdk as unknown as ObserveSDK, { ready: false })

		await Promise.all([first, second])
		expect(sdk.start).toHaveBeenCalledTimes(1)
	})

	it('cancels a pending start on stop() before registration', async () => {
		const sdk = fakeObserveSDK()
		const observe = freshObserve()

		const started = observe.start()
		observe.stop()
		await started

		observe.load(sdk as unknown as ObserveSDK, { ready: false })
		// Telemetry keeps buffering since the SDK was never started.
		observe.recordLog('A: after cancelled start', 'info')

		expect(sdk.start).not.toHaveBeenCalled()
		expect(sdk.recordLog).not.toHaveBeenCalled()
		expect((observe as unknown as { _isReady: boolean })._isReady).toBe(
			false,
		)
	})

	it('shares one sdk start between overlapping start() calls after registration', async () => {
		const sdk = fakeObserveSDK()
		let finishStart!: () => void
		sdk.start.mockReturnValue(
			new Promise<void>((resolve) => {
				finishStart = resolve
			}),
		)
		const observe = freshObserve()

		// An early start() that load() honors, overlapping a later start().
		const early = observe.start()
		observe.load(sdk as unknown as ObserveSDK, { ready: false })
		const late = observe.start()
		const later = observe.start()

		finishStart()
		await Promise.all([early, late, later])
		expect(sdk.start).toHaveBeenCalledTimes(1)
	})

	it('stops the sdk after a start that was in flight when stop() ran', async () => {
		const sdk = fakeObserveSDK()
		const calls: string[] = []
		let finishStart!: () => void
		sdk.start.mockImplementation(
			() =>
				new Promise<void>((resolve) => {
					finishStart = () => {
						calls.push('start')
						resolve()
					}
				}),
		)
		sdk.stop.mockImplementation(() => calls.push('stop'))
		const observe = freshObserve()

		// stop() in the same turn as registration, while the deferred start runs.
		const started = observe.start()
		observe.load(sdk as unknown as ObserveSDK, { ready: false })
		const stopped = observe.stop()

		finishStart()
		await Promise.all([started, stopped])
		expect(calls).toEqual(['start', 'stop'])
	})

	it('starts the sdk again for a start() made after a stop() that is still waiting', async () => {
		const sdk = fakeObserveSDK()
		const calls: string[] = []
		let finishStart!: () => void
		sdk.start.mockImplementationOnce(
			() =>
				new Promise<void>((resolve) => {
					finishStart = () => {
						calls.push('start')
						resolve()
					}
				}),
		)
		sdk.start.mockImplementation(() => calls.push('start'))
		sdk.stop.mockImplementation(() => calls.push('stop'))
		const observe = freshObserve()

		// start -> stop -> start while the first start is still running: the
		// last call wins, so the sdk must end up started.
		observe.load(sdk as unknown as ObserveSDK, { ready: false })
		const first = observe.start()
		const stopped = observe.stop()
		const second = observe.start()
		const third = observe.start()

		finishStart()
		await Promise.all([first, stopped, second, third])
		expect(calls).toEqual(['start', 'stop', 'start'])
	})

	it('waits for an idle stop() to finish shutting down before the next start()', async () => {
		const sdk = fakeObserveSDK()
		const calls: string[] = []
		let finishShutdown!: () => void
		sdk.start.mockImplementation(async () => {
			calls.push('start')
		})
		sdk.stop.mockImplementation(
			() =>
				new Promise<void>((resolve) => {
					calls.push('stop')
					finishShutdown = () => {
						calls.push('shutdown')
						resolve()
					}
				}),
		)
		const observe = freshObserve()
		observe.load(sdk as unknown as ObserveSDK, { ready: false })
		await observe.start()

		// Nothing in flight: stop() then start() right away.
		const stopped = observe.stop()
		const restarted = observe.start()
		await Promise.resolve()
		await Promise.resolve()
		expect(calls).toEqual(['start', 'stop'])

		finishShutdown()
		await Promise.all([stopped, restarted])
		expect(calls).toEqual(['start', 'stop', 'shutdown', 'start'])
	})

	it('waits for the plugin-started sdk before running a stop()', async () => {
		const sdk = fakeObserveSDK()
		const calls: string[] = []
		let finishStart!: () => void
		const started = new Promise<void>((resolve) => {
			finishStart = () => {
				calls.push('start')
				resolve()
			}
		})
		sdk.stop.mockImplementation(() => calls.push('stop'))
		const observe = freshObserve()

		// Without manualStart the plugin starts the sdk itself and hands that
		// start to load(); a stop() made meanwhile must run after it.
		observe.load(sdk as unknown as ObserveSDK, { ready: true, started })
		const stopped = observe.stop()
		await Promise.resolve()
		expect(sdk.stop).not.toHaveBeenCalled()

		finishStart()
		await stopped
		expect(calls).toEqual(['start', 'stop'])
	})

	it('passes a start() made after registration to the sdk', async () => {
		const sdk = fakeObserveSDK()
		const observe = freshObserve()

		observe.load(sdk as unknown as ObserveSDK, { ready: false })
		await observe.start()

		expect(sdk.start).toHaveBeenCalledTimes(1)
	})
})

describe('LDRecord start/stop safety', () => {
	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('defers start() to run once the plugin registers', async () => {
		const start = vi.fn()
		const stop = vi.fn()
		const record = freshRecord()

		// start() before any plugin registration must not throw.
		const started = record.start({ forceNew: true })
		record.load({ start, stop } as never, { ready: false })
		await started

		expect(start).toHaveBeenCalledWith({ forceNew: true })
		record.stop()
		expect(stop).toHaveBeenCalled()
	})

	it('does not start the sdk again when it was already started by the plugin', async () => {
		const start = vi.fn()
		const stop = vi.fn()
		const record = freshRecord()

		// Without manualStart the plugin starts the sdk itself and attaches it
		// as ready, so an early start() only has to resolve.
		const started = record.start()
		record.load({ start, stop } as never, { ready: true })
		await started

		expect(start).not.toHaveBeenCalled()
		expect((record as unknown as { _isReady: boolean })._isReady).toBe(true)
	})

	it('reads getters directly while attached but not started', () => {
		const getSession = vi.fn(() => ({ sessionSecureID: 'abc' }))
		const record = freshRecord()

		expect(record.getSession()).toBeNull()
		record.load({ getSession } as never, { ready: false })

		expect(record.getSession()).toEqual({ sessionSecureID: 'abc' })
		expect(
			(record as unknown as { _callBuffer: unknown[] })._callBuffer,
		).toHaveLength(0)
	})

	it('stops the sdk after a start that was in flight when stop() ran', async () => {
		const calls: string[] = []
		let finishStart!: () => void
		const start = vi.fn(
			() =>
				new Promise<void>((resolve) => {
					finishStart = () => {
						calls.push('start')
						resolve()
					}
				}),
		)
		const stop = vi.fn(() => calls.push('stop'))
		const record = freshRecord()

		const started = record.start()
		record.load({ start, stop } as never, { ready: false })
		record.start()
		record.stop()

		finishStart()
		await started
		await Promise.resolve()
		expect(start).toHaveBeenCalledTimes(1)
		expect(calls).toEqual(['start', 'stop'])
	})

	it('applies a start() made after a waiting stop(), with its options', async () => {
		const calls: unknown[] = []
		let finishStart!: () => void
		const start = vi.fn()
		start.mockImplementationOnce(
			(options?: unknown) =>
				new Promise<void>((resolve) => {
					finishStart = () => {
						calls.push(['start', options])
						resolve()
					}
				}),
		)
		start.mockImplementation((options?: unknown) =>
			calls.push(['start', options]),
		)
		const stop = vi.fn(() => calls.push('stop'))
		const record = freshRecord()

		record.load({ start, stop } as never, { ready: false })
		const first = record.start()
		record.stop()
		const second = record.start({ forceNew: true })

		finishStart()
		await Promise.all([first, second])
		expect(calls).toEqual([
			['start', undefined],
			'stop',
			['start', { forceNew: true }],
		])
	})

	it('cancels a pending start on stop() before registration', async () => {
		const start = vi.fn()
		const stop = vi.fn()
		const record = freshRecord()

		const started = record.start()
		record.stop()
		await started

		record.load({ start, stop } as never, { ready: false })
		// Telemetry keeps buffering since the SDK was never started.
		record.addSessionProperties({ plan: 'pro' })

		expect(start).not.toHaveBeenCalled()
		expect(stop).not.toHaveBeenCalled()
		expect((record as unknown as { _isReady: boolean })._isReady).toBe(
			false,
		)
	})
})

describe('observe plugin registration', () => {
	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('attaches the sdk unstarted with manualStart', () => {
		const startSpy = vi
			.spyOn(ObserveSDK.prototype, 'start')
			.mockResolvedValue(undefined)
		const loadSpy = vi
			.spyOn(LDObserve, 'load')
			.mockImplementation(() => undefined)

		const plugin = new ObservePlugin({ manualStart: true })
		plugin.getHooks?.({
			sdkKey: 'test-client-side-id',
			sdk: { name: 'js-sdk', version: '1.0.0' },
		})

		expect(startSpy).not.toHaveBeenCalled()
		expect(loadSpy).toHaveBeenCalledWith(expect.any(ObserveSDK), {
			ready: false,
		})
	})

	it('starts and attaches the sdk as ready by default', () => {
		const startSpy = vi
			.spyOn(ObserveSDK.prototype, 'start')
			.mockResolvedValue(undefined)
		const loadSpy = vi
			.spyOn(LDObserve, 'load')
			.mockImplementation(() => undefined)

		const plugin = new ObservePlugin({})
		plugin.getHooks?.({
			sdkKey: 'test-client-side-id',
			sdk: { name: 'js-sdk', version: '1.0.0' },
		})

		expect(startSpy).toHaveBeenCalled()
		expect(loadSpy).toHaveBeenCalledWith(expect.any(ObserveSDK), {
			ready: true,
			started: expect.any(Promise),
		})
	})
})
