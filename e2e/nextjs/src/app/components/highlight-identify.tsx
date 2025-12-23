'use client'

import { H } from '@highlight-run/next/client'
import { useEffect, useMemo } from 'react'
import { CONSTANTS } from '@/constants'

export function HighlightIdentify() {
	const ldClientPromise = useMemo(async () => {
		const { createClient } = await import('@launchdarkly/js-client-sdk')
		const ldClient = createClient(
			CONSTANTS.NEXT_PUBLIC_LAUNCHDARKLY_SDK_KEY ?? '',
			{
				kind: 'multi',
				user: { key: 'bob' },
				org: { key: 'MacDonwalds' },
			},
		)
		H.registerLD(ldClient)
		await ldClient.start()

		return ldClient
	}, [])
	useEffect(() => {
		;(async () => {
			const ldClient = await ldClientPromise
			await ldClient.identify({
				kind: 'multi',
				user: { key: 'bob' },
				org: { key: 'MacDonwalds' },
			})
		})()
	}, [ldClientPromise])

	return null
}
