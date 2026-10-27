import { Logger } from '../client/logger'
import { internalLog } from './util'

type Event = { method: string; args: any[] }

export interface BufferedLoadOptions {
	/**
	 * Whether the attached SDK has already been started.
	 * When `false` (e.g. `manualStart` mode), calls keep buffering until the
	 * SDK is started; a `start()` made before `load` runs at `load` time.
	 */
	ready?: boolean
}

interface PendingStart {
	promise: Promise<void>
	resolve: () => void
}

export class BufferedClass<T extends object> {
	protected _sdk!: T
	/** True once an SDK instance has been attached via `load`. */
	protected _isLoaded = false
	/** True once the attached SDK has started and the buffer has drained. */
	protected _isReady = false
	protected _callBuffer: Array<Event> = []
	protected _capacity: number = 100_000
	protected _droppedEvents: number = 0
	protected _exceededCapacity: boolean = false
	protected _logger = new Logger()
	private _pendingStart?: PendingStart
	/**
	 * The last start or stop queued on the attached SDK while it runs. Starts
	 * and stops run in call order, so the final state follows the last call.
	 */
	private _lastOp?: { kind: 'start' | 'stop'; promise: Promise<unknown> }

	protected _bufferCall(method: string, args: any[]) {
		if (this._isReady) {
			// If ready, execute the method directly
			return this._callSdk(method, args)
		} else {
			// Otherwise buffer the call
			this._enqueue({ method, args })
			return undefined
		}
	}

	/** Calls the attached SDK directly, logging instead of throwing. */
	protected _callSdk(method: string, args: any[]) {
		try {
			return (this._sdk as any)[method](...args)
		} catch (error) {
			internalLog(
				`Error executing buffered call to ${method}:`,
				'error',
				error,
			)
		}
	}

	protected _enqueue(event: Event) {
		if (this._callBuffer.length < this._capacity) {
			this._callBuffer.push(event)
			this._exceededCapacity = false
		} else {
			if (!this._exceededCapacity) {
				this._exceededCapacity = true
				internalLog(
					'Exceeded event queue capacity. Increase capacity to avoid dropping events.',
					'warn',
				)
			}
			this._droppedEvents += 1
		}
	}

	/**
	 * Attach an SDK implementation. With `ready: false` (e.g. `manualStart`
	 * mode, or an SDK whose `start` is still running), telemetry keeps
	 * buffering until `markReady()` is called.
	 */
	load(sdk: T, { ready = true }: BufferedLoadOptions = {}) {
		this._sdk = sdk
		this._isLoaded = true

		const pending = this._pendingStart
		this._pendingStart = undefined
		if (ready) {
			// The SDK is already started (not `manualStart`), so an early
			// `start()` must not start it a second time.
			this.markReady()
			pending?.resolve()
		} else if (pending) {
			// `start()` was called before any SDK was attached: honor it now.
			void this._runPendingStart(pending)
		}
	}

	private async _runPendingStart(pending: PendingStart) {
		try {
			await this._start()
		} catch (error) {
			internalLog(`Error starting the SDK`, 'error', error)
		}
		pending.resolve()
	}

	/**
	 * Starts the attached SDK and drains the buffer. Before `load` the start
	 * is deferred. Starts and stops run in call order: a start made while a
	 * stop is queued runs after that stop, while overlapping starts with no
	 * stop between them share one SDK start.
	 */
	protected _start(): Promise<void> {
		if (!this._isLoaded) {
			return this._deferStart()
		}
		if (this._lastOp?.kind === 'start') {
			return this._lastOp.promise as Promise<void>
		}
		return this._queueOp('start', async () => {
			try {
				await this._startSdk()
			} finally {
				this.markReady()
			}
		})
	}

	/**
	 * Stops the attached SDK. Before `load` it cancels a deferred start; while
	 * a start or stop is running it waits for it, so an earlier start cannot
	 * undo the stop.
	 */
	protected _stop<R>(stopSdk: () => R): R | Promise<R> | undefined {
		if (!this._isLoaded) {
			this._cancelPendingStart()
			return undefined
		}
		if (this._lastOp) {
			return this._queueOp('stop', async () => stopSdk())
		}
		return stopSdk()
	}

	/** Runs `op` once the previously queued start or stop has settled. */
	private _queueOp<R>(
		kind: 'start' | 'stop',
		op: () => Promise<R>,
	): Promise<R> {
		const previous = this._lastOp?.promise
		const promise = (previous ? previous.then(op, op) : op()).finally(
			() => {
				if (this._lastOp?.promise === promise) {
					this._lastOp = undefined
				}
			},
		)
		this._lastOp = { kind, promise }
		return promise
	}

	/**
	 * Starts the attached SDK. Subclasses whose SDK owns a `start` method
	 * override this so a deferred start reaches the real implementation.
	 */
	protected async _startSdk(): Promise<void> {}

	/**
	 * Drains the buffered calls into the attached SDK and lets subsequent
	 * calls run directly.
	 */
	protected markReady() {
		if (this._isReady) {
			return
		}
		this._isReady = true

		// Process buffered calls
		for (const { method, args } of this._callBuffer) {
			try {
				;(this._sdk as any)[method](...args)
			} catch (error) {
				internalLog(
					`Error executing buffered call to ${method}:`,
					'error',
					error,
				)
			}
		}

		// Clear the buffer
		this._callBuffer = []
	}

	/**
	 * Defers a start until `load` attaches an SDK. Returns a promise that
	 * resolves once the SDK has been attached, started and drained.
	 */
	protected _deferStart(): Promise<void> {
		if (!this._pendingStart) {
			let resolve!: () => void
			const promise = new Promise<void>((res) => {
				resolve = res
			})
			this._pendingStart = { promise, resolve }
		}
		return this._pendingStart.promise
	}

	/** Cancels a deferred start so it never runs; awaiting callers resolve. */
	protected _cancelPendingStart() {
		if (this._pendingStart) {
			this._pendingStart.resolve()
			this._pendingStart = undefined
		}
	}
}
