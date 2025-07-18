import { initialize as init } from 'launchdarkly-js-client-sdk'
import Observability, { LDObserve } from '@launchdarkly/observability'
import SessionReplay, { LDRecord } from '@launchdarkly/session-replay'

const observabilitySettings: ConstructorParameters<typeof Observability>[0] = {
	networkRecording: {
		enabled: true,
		recordHeadersAndBody: true,
	},
	serviceName: 'ryan-test',
	backendUrl: 'http://localhost:8082/public',
	otel: {
		otlpEndpoint: 'http://localhost:4318',
	},
	manualStart: true,
	sessionKey: 'task123',
}
const sessionReplaySettings: ConstructorParameters<typeof SessionReplay>[0] = {
	debug: { clientInteractions: true, domRecording: true },
	privacySetting: 'none',
	serviceName: 'ryan-test',
	backendUrl: 'http://localhost:8082/public',
	manualStart: true,
	sessionKey: 'task123',
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

export const recordSession = async () => {
	console.log('Session recording enabled via LaunchDarkly')
	await LDRecord.start()
}

export const recordObservability = async () => {
	console.log('Observability enabled via LaunchDarkly')
	await LDObserve.start()
}
