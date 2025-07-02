// import { initialize as init } from '@launchdarkly/js-client-sdk'
import { initialize as init } from 'launchdarkly-js-client-sdk'
import Observability from '@launchdarkly/observability'
import SessionReplay from '@launchdarkly/session-replay'
// import { LD } from '@launchdarkly/browser'
// import { withLDProvider } from 'launchdarkly-react-client-sdk'

const observabilitySettings: ConstructorParameters<typeof Observability>[0] = {
	networkRecording: {
		enabled: true,
		recordHeadersAndBody: true,
	},
	serviceName: 'ryan-test',
	backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com',
	otel: {
		otlpEndpoint: 'https://otel.observability.ld-stg.launchdarkly.com',
	},
}
const sessionReplaySettings: ConstructorParameters<typeof SessionReplay>[0] = {
	debug: { clientInteractions: true, domRecording: true },
	privacySetting: 'none',
	serviceName: 'ryan-test',
	backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com',
}

export const client = init(
	'548f6741c1efad40031b18ae',
	{ key: 'unknown' },
	{
		// Not including plugins at all would be equivalent to the current LaunchDarkly SDK.
		plugins: [
			new Observability(observabilitySettings),
			new SessionReplay(sessionReplaySettings),
		],
		baseUrl: 'https://ld-stg.launchdarkly.com',
		eventsUrl: 'https://events-stg.launchdarkly.com',
	},
)

/*export const LDProvider = withLDProvider({
	clientSideID: '66d9d3c255856f0fa8fd62d0',
	context: { key: 'unknown' },
	options: {
		plugins: [
			new Observability(observabilitySettings),
			new SessionReplay(sessionReplaySettings),
		],
	},
})*/
