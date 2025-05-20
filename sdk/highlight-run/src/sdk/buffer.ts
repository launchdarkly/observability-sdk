import { Logger } from '../client/logger'
import { recordWarning } from './util'

type Event = { method: string; args: any[] }

export class BufferedClass<T extends object> {
	protected _sdk!: T
	protected _isLoaded = false
	protected _callBuffer: Array<Event> = []
	protected _capacity: number = 100_000
	protected _droppedEvents: number = 0
	protected _exceededCapacity: boolean = false
	protected _logger = new Logger()

	protected _bufferCall(method: string, args: any[]) {
		if (this._isLoaded) {
			// If already loaded, execute the method directly
			try {
				return (this._sdk as any)[method](...args)
			} catch (error) {
				recordWarning(
					`Error executing buffered call to ${method}:`,
					error,
				)
			}
		} else {
			// Otherwise buffer the call
			this._enqueue({ method, args })
			return undefined
		}
	}

	protected _enqueue(event: Event) {
		if (this._callBuffer.length < this._capacity) {
			this._callBuffer.push(event)
			this._exceededCapacity = false
		} else {
			if (!this._exceededCapacity) {
				this._exceededCapacity = true
				recordWarning(
					'Exceeded event queue capacity. Increase capacity to avoid dropping events.',
				)
			}
			this._droppedEvents += 1
		}
	}

	load(sdk: T) {
		this._sdk = sdk
		this._isLoaded = true

		// Process buffered calls
		for (const { method, args } of this._callBuffer) {
			try {
				;(this._sdk as any)[method](...args)
			} catch (error) {
				recordWarning(
					`Error executing buffered call to ${method}:`,
					error,
				)
			}
		}

		// Clear the buffer
		this._callBuffer = []
	}
}
