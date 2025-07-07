type Event = { method: string; args: any[] }

export class BufferedClass<T extends object> {
	protected _sdk!: T
	protected _isLoaded = false
	protected _callBuffer: Array<Event> = []
	protected _capacity: number = 1000 // Prevent memory issues
	protected _droppedEvents: number = 0
	protected _exceededCapacity: boolean = false

	protected _bufferCall(method: string, args: any[]) {
		if (this._isLoaded) {
			// If already loaded, execute the method directly
			try {
				return (this._sdk as any)[method](...args)
			} catch (error) {
				console.warn(
					`LDObserve: Error executing buffered call to ${method}:`,
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
				console.warn(
					'LDObserve: Exceeded event queue capacity. Increase capacity to avoid dropping events.',
				)
			}
			this._droppedEvents += 1
		}
	}

	load(sdk: T) {
		this._sdk = sdk
		this._isLoaded = true

		console.log(
			`LDObserve: Processing ${this._callBuffer.length} buffered calls`,
		)

		// Process buffered calls
		for (const { method, args } of this._callBuffer) {
			try {
				;(this._sdk as any)[method](...args)
			} catch (error) {
				console.warn(
					`LDObserve: Error executing buffered call to ${method}:`,
					error,
				)
			}
		}

		// Clear the buffer
		this._callBuffer = []
	}

	// Internal methods for testing
	_getBufferStatus() {
		return {
			buffer: this._callBuffer,
			bufferSize: this._callBuffer.length,
			droppedEvents: this._droppedEvents,
			isLoaded: this._isLoaded,
			exceededCapacity: this._exceededCapacity,
		}
	}

	_resetForTesting() {
		this._callBuffer = []
		this._droppedEvents = 0
		this._exceededCapacity = false
		this._isLoaded = false
		this._sdk = undefined as any
	}
}
