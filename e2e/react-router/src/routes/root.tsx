import { initialize } from '@launchdarkly/js-client-sdk'
import Observability from '@launchdarkly/observability'
import SessionReplay from '@launchdarkly/session-replay'

const client = initialize('client-side-id-123abc', {
	// Not including plugins at all would be equivalent to the current LaunchDarkly SDK.
	plugins: [
		new Observability('1', {
			networkRecording: {
				enabled: true,
				recordHeadersAndBody: true,
			},
		}),
		new SessionReplay('1', {
			// TODO(vkorolik) don't apply to SR
			networkRecording: {
				enabled: true,
				recordHeadersAndBody: true,
			},
		}), // Could be omitted for customers who cannot use session replay.
	],
})

export default function Root() {
	return (
		<div id="sidebar">
			<h1>Hello, world</h1>
			<p>{JSON.stringify(client.allFlags())}</p>
		</div>
	)
}
