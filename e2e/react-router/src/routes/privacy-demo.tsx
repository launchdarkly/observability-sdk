import { useEffect } from 'react'
import { initialize as init } from 'launchdarkly-js-client-sdk'
import SessionReplay from '@launchdarkly/session-replay'

export default function PrivacyDemo() {
	useEffect(() => {
		init(
			'548f6741c1efad40031b18ae',
			{ key: 'unknown' },
			{
				plugins: [
					new SessionReplay({
						privacySetting: 'none',
						serviceName: 'privacy-test',
						backendUrl: 'http://localhost:8082/public',
						debug: { clientInteractions: true, domRecording: true },

						maskTextClass: 'ld-mask-text',
						maskTextSelector: '[data-masking="true"]',
						ignoreClass: 'ld-ignore',
						ignoreSelector: '[data-ignore="true"]',
						blockClass: 'ld-block',
						blockSelector: '[data-block="true"]',
					}),
				],
			},
		)
	}, [])

	return (
		<div style={{ display: 'grid', gap: 16 }}>
			<h2>Session Replay Privacy Demo</h2>
			<p>
				This page showcases rrweb privacy options exposed via
				@launchdarkly/session-replay.
			</p>
			<section>
				<h3>Masked text by class</h3>
				<p>
					<span>Visible text</span>
				</p>
				<p className="ld-mask-text">
					<span>Secret text</span>
				</p>
				<div className="ld-mask-text">
					Test a string with shorter words like in and with extra
					{'    '}spaces.
				</div>
			</section>

			<section>
				<h3>Masked text by selector</h3>
				<div data-masking="true">
					This subtree should be masked by selector
					<div>
						<div>Deep child content</div>
					</div>
				</div>
			</section>

			<section>
				<h3>Ignored inputs by class</h3>
				<label>
					Not ignored input:
					<input type="text" placeholder="Type here (recorded)" />
				</label>
				<label>
					Ignored input (class):
					<input
						className="ld-ignore"
						type="text"
						placeholder="Type here (ignored)"
					/>
				</label>
			</section>

			<section>
				<h3>Ignored inputs by selector</h3>
				<label>
					Ignored input (selector):
					<input
						data-ignore="true"
						type="text"
						placeholder="Type here (ignored)"
					/>
				</label>
			</section>

			<section>
				<h3>Blocked subtree by class</h3>
				<div
					className="ld-block"
					style={{ border: '1px solid #ccc', padding: 8 }}
				>
					<p>Contents should be blocked (not recorded)</p>
					<img src="/vite.svg" alt="example" width={48} height={48} />
				</div>
			</section>

			<section>
				<h3>Blocked subtree by selector</h3>
				<div
					data-block="true"
					style={{ border: '1px solid #ccc', padding: 8 }}
				>
					<p>Contents should be blocked (not recorded)</p>
					<img src="/vite.svg" alt="example" width={48} height={48} />
				</div>
			</section>
		</div>
	)
}
