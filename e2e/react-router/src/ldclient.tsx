// import { initialize as init } from '@launchdarkly/js-client-sdk'
import { initialize as init } from 'launchdarkly-js-client-sdk'
import Observability from '@launchdarkly/observability'
import SessionReplay from '@launchdarkly/session-replay'
// import { LD } from '@launchdarkly/browser'

export const client = init(
	'66d9d3c255856f0fa8fd62d0',
	{ key: 'unknown' },
	{
		// Not including plugins at all would be equivalent to the current LaunchDarkly SDK.
		plugins: [
			new Observability('1', {
				networkRecording: {
					enabled: true,
					recordHeadersAndBody: true,
				},
				serviceName: 'ryan-test',
				backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com',
				otel: {
					otlpEndpoint:
						'https://otel.observability.ld-stg.launchdarkly.com',
				},
			}),
			new SessionReplay('1', {
				debug: { clientInteractions: true, domRecording: true },
				privacySetting: 'none',
				serviceName: 'ryan-test',
				backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com',
			}), // Could be omitted for customers who cannot use session replay.
		],
	},
)
