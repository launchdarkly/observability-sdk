export class BufferedClass<T extends object> {
	protected _sdk!: T
	protected _isLoaded = false
	protected _callBuffer: Array<{ method: string; args: any[] }> = []

	protected _bufferCall(method: string, args: any[]) {
		if (this._isLoaded) {
			// If already loaded, execute the method directly
			return (this._sdk as any)[method](...args)
		} else {
			// Otherwise buffer the call
			this._callBuffer.push({ method, args })
			return undefined
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
				console.error(
					`Error executing buffered call to ${method}:`,
					error,
				)
			}
		}

		// Clear the buffer
		this._callBuffer = []
	}
}
