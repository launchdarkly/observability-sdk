import type {
	HighlightOptions,
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
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { Hook } from '../integrations/launchdarkly'

export interface Record {
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
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[]
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
