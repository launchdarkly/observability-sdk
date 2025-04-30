import type { Source } from '../client/types/shared-types'
import type {
	HighlightOptions,
	Metadata,
	PrivacySettingOption,
	SamplingStrategy,
	SessionDetails,
	StartOptions,
} from '../client/types/types'
import type {
	ConsoleMethods,
	DebugOptions,
	NetworkRecordingOptions,
	SessionShortcutOptions,
} from '../client/types/client'
import type { LDClientMin } from '../integrations/launchdarkly/types/LDClient'
import { LDPluginEnvironmentMetadata } from '../plugins/plugin'

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
	start: (options?: StartOptions) => Promise<void>
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
	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void
}

export type RecordOptions = {
	organizationID: number | string
	debug?: boolean | DebugOptions
	backendUrl?: string
	tracingOrigins?: boolean | (string | RegExp)[]
	disableNetworkRecording?: boolean
	networkRecording?: boolean | NetworkRecordingOptions
	disableBackgroundRecording?: boolean
	disableConsoleRecording?: boolean
	disableSessionRecording?: boolean
	reportConsoleErrors?: boolean
	consoleMethodsToRecord?: ConsoleMethods[]
	privacySetting?: PrivacySettingOption
	enableSegmentIntegration?: boolean
	enableCanvasRecording?: boolean
	enablePerformanceRecording?: boolean
	enablePromisePatch?: boolean
	samplingStrategy?: SamplingStrategy
	inlineImages?: boolean
	inlineVideos?: boolean
	inlineStylesheet?: boolean
	recordCrossOriginIframe?: boolean
	firstloadVersion?: string
	environment?: 'development' | 'production' | 'staging' | string
	appVersion?: string
	serviceName?: string
	sessionShortcut?: SessionShortcutOptions
	sessionSecureID: string // Introduced in firstLoad 3.0.1
	storageMode?: 'sessionStorage' | 'localStorage'
	sessionCookie?: true
	sendMode?: 'webworker' | 'local'
	otlpEndpoint?: HighlightOptions['otlpEndpoint']
	otel?: HighlightOptions['otel']
}
