import type { RecordOptions } from '../client/types/record'
import type { ObserveOptions } from '../client/types/observe'
import { setCookieWriteEnabled, setStorageMode } from '../client/utils/storage'
import {
	getPreviousSessionData,
	loadCookieSessionData,
} from '../client/utils/sessionStorage/highlightSession'
import { GenerateSecureID } from '../client'

export class Plugin<T extends RecordOptions | ObserveOptions> {
	protected sessionSecureID!: string
	protected readonly initCalled: boolean = false
	constructor(options?: T) {
		if (options?.storageMode) {
			setStorageMode(options?.storageMode)
		}
		setCookieWriteEnabled(!!options?.sessionCookie)

		if (options?.sessionCookie) {
			loadCookieSessionData()
		} else {
			setCookieWriteEnabled(false)
		}

		let previousSession = getPreviousSessionData()
		this.sessionSecureID = GenerateSecureID()
		if (previousSession?.sessionSecureID) {
			this.sessionSecureID = previousSession.sessionSecureID
		}

		// `init` was already called, do not reinitialize
		if (this.initCalled) {
			return
		}
		this.initCalled = true
	}
}
