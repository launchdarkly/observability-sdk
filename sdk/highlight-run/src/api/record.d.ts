import type { Source } from '../client/types/shared-types'
import type {
	HighlightOptions,
	SessionDetails,
	StartOptions,
} from '../client/types/types'

export interface Record {
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
	/**
	 * Start the session when running in `manualStart` mode.
	 * Can be used to force start a new session.
	 * @param options the session start options.
	 */
	start: (options?: StartOptions) => void
	/**
	 * Stop the session recording.
	 */
	stop: () => void
	/**
	 * Snapshot an HTML <canvas> element in WebGL manual snapshotting mode.
	 * See {@link https://www.highlight.io/docs/getting-started/browser/replay-configuration/canvas#manual-snapshotting}
	 * for more information.
	 */
	snapshot: (element: HTMLCanvasElement) => Promise<void>
	getSession: () => SessionDetails | null
	getRecordingState: () => 'NotRecording' | 'Recording'
}
