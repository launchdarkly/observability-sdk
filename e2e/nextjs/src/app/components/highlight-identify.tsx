'use client'

import { H } from '@highlight-run/next/client'
import { useEffect, useMemo } from 'react'
import { CONSTANTS } from '@/constants'

export function HighlightIdentify() {
	const ldClientPromise = useMemo(async () => {
		const { initialize } = await import('@launchdarkly/js-client-sdk')
		const ldClient = initialize(
			CONSTANTS.NEXT_PUBLIC_LAUNCHDARKLY_SDK_KEY ?? '',
		)
		H.registerLD(ldClient)
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
