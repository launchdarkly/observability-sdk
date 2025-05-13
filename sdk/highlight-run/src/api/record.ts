import type { SessionDetails, StartOptions } from '../client/types/types'
import type { LDClient } from '../integrations/launchdarkly'
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
		client: LDClient,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[]
}
