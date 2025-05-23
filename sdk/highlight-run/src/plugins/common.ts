import type { RecordOptions } from '../client/types/record'
import type { ObserveOptions } from '../client/types/observe'
import { setCookieWriteEnabled, setStorageMode } from '../client/utils/storage'
import {
	getPersistentSessionSecureID,
	getPreviousSessionData,
	loadCookieSessionData,
	setSessionData,
	setSessionSecureID,
} from '../client/utils/sessionStorage/highlightSession'
import { GenerateSecureID } from '../client'
import { internalLog } from '../sdk/util'

export class Plugin<T extends RecordOptions | ObserveOptions> {
	protected sessionSecureID!: string
	constructor(options?: T) {
		try {
			if (options?.storageMode) {
				setStorageMode(options?.storageMode)
			}
			setCookieWriteEnabled(!!options?.sessionCookie)

			if (options?.sessionCookie) {
				loadCookieSessionData()
			} else {
				setCookieWriteEnabled(false)
			}

			const persistentSessionSecureID = getPersistentSessionSecureID()
			let previousSession = getPreviousSessionData(
				persistentSessionSecureID,
			)
			if (previousSession?.sessionSecureID) {
				this.sessionSecureID = previousSession.sessionSecureID
			} else {
				this.sessionSecureID = GenerateSecureID()
				setSessionSecureID(this.sessionSecureID)
				setSessionData({
					sessionSecureID: this.sessionSecureID,
					projectID: 0,
					payloadID: 1,
					sessionStartTime: Date.now(),
					lastPushTime: Date.now(),
				})
			}
		} catch (error) {
			internalLog(
				`Error initializing @launchdarkly observability plugin`,
				'error',
				error,
			)
		}
	}
}
