import { asyncWithLDProvider } from 'launchdarkly-react-client-sdk'
import Observability from '@launchdarkly/observability'
import SessionReplay from '@launchdarkly/session-replay'

const LDProvider = asyncWithLDProvider({
	clientSideID: '548f6741c1efad40031b18ae',
	context: {
		kind: 'user',
		key: 'user-key-123abc',
		name: 'Sandy Smith',
		email: 'sandy@example.com',
	},
	options: {
		plugins: [
			new Observability({
				networkRecording: {
					enabled: true,
					recordHeadersAndBody: true,
				},
			}),
		],
	},
})
export default LDProvider
