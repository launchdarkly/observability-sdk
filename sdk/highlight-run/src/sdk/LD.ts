export class SDKCore {
	protected _isLoaded = false
	protected _callBuffer: Array<{ method: string; args: any[] }> = []

	protected _bufferCall(method: string, args: any[]) {
		if (this._isLoaded) {
			// If already loaded, execute the method directly
			return (this as any)[method](...args)
		} else {
			// Otherwise buffer the call
			this._callBuffer.push({ method, args })
			return undefined
		}
	}

	// TODO(vkorolik) buffer until identify
	async load(sdks: Promise<object>[]) {
		// Load the modules
		await Promise.all(
			sdks.map(async (module: any) => {
				const klass = await module
				const proto = klass.prototype
				for (const key of Reflect.ownKeys(proto)) {
					const desc = Object.getOwnPropertyDescriptor(proto, key)
					if (key === 'constructor' || !desc) {
						continue
					}
					Object.defineProperty(this, key, desc)
				}
			}),
		)

		// Mark as loaded
		this._isLoaded = true

		// Process buffered calls
		for (const { method, args } of this._callBuffer) {
			try {
				;(this as any)[method](...args)
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
