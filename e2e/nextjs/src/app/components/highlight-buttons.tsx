'use client'

import { Button } from '@/app/components/button'
import { H } from '@highlight-run/next/client'
import { useMemo } from 'react'
import { CONSTANTS } from '@/constants'

export function HighlightButtons() {
	const ldClientPromise = useMemo(async () => {
		const { initialize } = await import('@launchdarkly/js-client-sdk')
		const ldClient = initialize(
			CONSTANTS.NEXT_PUBLIC_LAUNCHDARKLY_SDK_KEY ?? '',
		)
		H.registerLD(ldClient)
		return ldClient
	}, [])
	return (
		<div
			style={{
				display: 'grid',
				gridTemplateColumns: '20rem',
				gridGap: '1rem',
				padding: '2rem',
			}}
		>
			<Button
				onClick={async () => {
					const ldClient = await ldClientPromise
					const flag = ldClient.variation('my-boolean-flag', true)
					console.log('flag', flag)
				}}
			>
				Variation
			</Button>
			<Button
				onClick={async () => {
					const ldClient = await ldClientPromise
					await ldClient.identify({
						kind: 'multi',
						user: { key: 'vadim' },
						org: { key: 'tester' },
					})
				}}
			>
				Identify
			</Button>
			<Button
				onClick={() => {
					H.track('clicked track event', { random: Math.random() })
				}}
			>
				Track
			</Button>
			<Button
				onClick={() => {
					H.recordMetric({ name: 'my-metric', value: Math.random() })
				}}
			>
				H.recordMetric
			</Button>
			<Button
				onClick={() => {
					H.start()
				}}
			>
				Start
			</Button>
			<Button
				onClick={() => {
					H.stop()
				}}
			>
				Stop
			</Button>
		</div>
	)
}
