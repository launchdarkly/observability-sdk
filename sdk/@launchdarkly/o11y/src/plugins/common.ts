import type { RecordOptions } from '../client/types/record'
import type { ObserveOptions } from '../client/types/observe'
import {
	setCookieDomain,
	setCookieWriteEnabled,
	setStorageMode,
} from '../client/utils/storage'
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
			// Only override when set, so options without a domain (e.g. a second
			// plugin) keep a domain configured earlier.
			if (options?.sessionCookieDomain !== undefined) {
				setCookieDomain(options.sessionCookieDomain)
			}

			if (options?.sessionCookie) {
				loadCookieSessionData()
			} else {
				setCookieWriteEnabled(false)
			}
		} catch (error) {
			internalLog(
				`Error initializing @launchdarkly observability plugin`,
				'error',
				error,
			)
		}
	}

	configureSession(ldCredential: string, options?: T) {
		try {
			const persistentSessionSecureID = getPersistentSessionSecureID()
			let previousSession = getPreviousSessionData(
				persistentSessionSecureID,
			)
			if (previousSession?.sessionSecureID) {
				this.sessionSecureID = previousSession.sessionSecureID
			} else {
				if (options?.sessionKey) {
					this.sessionSecureID = GenerateSecureID(
						`${ldCredential}-${options.sessionKey}`,
					)
				} else {
					this.sessionSecureID = GenerateSecureID()
				}
				setSessionSecureID(this.sessionSecureID)
				setSessionData({
					sessionSecureID: this.sessionSecureID,
					sessionKey: options?.sessionKey,
					projectID: 0,
					sessionStartTime: Date.now(),
					lastPushTime: Date.now(),
				})
			}
		} catch (error) {
			internalLog(
				`Error configuring session in @launchdarkly observability plugin`,
				'error',
				error,
			)
		}
	}
}
