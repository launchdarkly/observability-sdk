import { useEffect } from 'react'
import { useFlags, useLDClient } from 'launchdarkly-react-client-sdk'
import Observability from '@launchdarkly/observability'
import SessionReplay from '@launchdarkly/session-replay'

function Welcome() {
	const { devTestFlag, observabilityEnabled } = useFlags()
	const client = useLDClient()

	useEffect(() => {
		if (observabilityEnabled && client) {
			const plugins = [new Observability(), new SessionReplay()]
			plugins.forEach((plugin) =>
				plugin.register(client, {
					sdk: { name: '', version: '' },
					clientSideId: '<launchdarkly-client-side-id>',
				}),
			)
		}
	}, [observabilityEnabled, client])

	return (
		<p>
			welcome to this page!
			<br />
			{devTestFlag ? <b>Flag on</b> : <b>Flag off</b>}
		</p>
	)
}

export default Welcome
