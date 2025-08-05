// import { initialize as init } from '@launchdarkly/js-client-sdk'
import { initialize as init } from 'launchdarkly-js-client-sdk'
import Observability from '@launchdarkly/observability'
import SessionReplay from '@launchdarkly/session-replay'
// import { LD } from '@launchdarkly/browser'
// import { withLDProvider } from 'launchdarkly-react-client-sdk'

// HTTP Configuration (traditional)
const observabilitySettingsHTTP: ConstructorParameters<
	typeof Observability
>[0] = {
	networkRecording: {
		enabled: true,
		recordHeadersAndBody: true,
	},
	serviceName: 'http-test',
	backendUrl: 'http://localhost:8082/public',
	//useWebSocket: false, // Explicitly use HTTP
	otel: {
		otlpEndpoint: 'http://localhost:8082/public',
	},
}

const sessionReplaySettingsHTTP: ConstructorParameters<
	typeof SessionReplay
>[0] = {
	debug: { clientInteractions: true, domRecording: true },
	privacySetting: 'none',
	serviceName: 'http-test',
	backendUrl: 'http://localhost:8082/public',
	//useWebSocket: false, // Explicitly use HTTP
}

// WebSocket Configuration (connection reuse)
const observabilitySettingsWS: ConstructorParameters<typeof Observability>[0] =
	{
		networkRecording: {
			enabled: true,
			recordHeadersAndBody: true,
		},
		serviceName: 'websocket-test',
		backendUrl: 'http://localhost:8082/public',
		//useWebSocket: true, // Enable WebSocket transport
		otel: {
			otlpEndpoint: 'http://localhost:8082/public',
		},
	}

const sessionReplaySettingsWS: ConstructorParameters<typeof SessionReplay>[0] =
	{
		debug: { clientInteractions: true, domRecording: true },
		privacySetting: 'none',
		serviceName: 'websocket-test',
		backendUrl: 'http://localhost:8082/public',
		//useWebSocket: true, // Enable WebSocket transport
	}

// Export clients for HTTP and WebSocket comparison
export const clientHTTP = init(
	'548f6741c1efad40031b18ae',
	{ key: 'unknown-http' },
	{
		plugins: [
			new Observability(observabilitySettingsHTTP),
			new SessionReplay(sessionReplaySettingsHTTP),
		],
		baseUrl: 'https://ld-stg.launchdarkly.com',
		eventsUrl: 'https://events-stg.launchdarkly.com',
	},
)

export const clientWS = init(
	'548f6741c1efad40031b18ae',
	{ key: 'unknown-websocket' },
	{
		plugins: [
			new Observability(observabilitySettingsWS),
			new SessionReplay(sessionReplaySettingsWS),
		],
		baseUrl: 'https://ld-stg.launchdarkly.com',
		eventsUrl: 'https://events-stg.launchdarkly.com',
	},
)

// Default export for backward compatibility (use HTTP)
export const client = clientHTTP

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
