import type { Source } from '../client/types/shared-types'
import type {
	HighlightOptions,
	SessionDetails,
	StartOptions,
} from '../client/types/types'

export interface Record {
	init: (
		projectID?: string | number,
		debug?: HighlightOptions,
	) => { sessionSecureID: string } | undefined
	/**
	 * Calling this will assign an identifier to the session.
	 * @example identify('teresa@acme.com', { accountAge: 3, cohort: 8 })
	 * @param identifier Is commonly set as an email or UUID.
	 * @param metadata Additional details you want to associate to the user.
	 */
	identify: (identifier: string, metadata?: Metadata, source?: Source) => void
	/**
	 * Call this to record when you want to track a specific event happening in your application.
	 * @example track('startedCheckoutProcess', { cartSize: 10, value: 85 })
	 * @param event The name of the event.
	 * @param metadata Additional details you want to associate to the event.
	 */
	track: (event: string, metadata?: Metadata) => void

	getSession: () => SessionDetails | null
	start: (options?: StartOptions) => void
	/** Stops the session and error recording. */
	stop: (options?: StartOptions) => void
	getRecordingState: () => 'NotRecording' | 'Recording'
	snapshot: (element: HTMLCanvasElement) => Promise<void>
}
