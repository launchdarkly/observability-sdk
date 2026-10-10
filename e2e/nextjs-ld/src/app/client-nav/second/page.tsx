'use client'

import Link from 'next/link'
import { useEffect } from 'react'

// A distinct client page so navigating between the two records route changes
// in the session replay + a console breadcrumb for each mount.
export default function ClientNavSecondPage() {
	useEffect(() => {
		console.info('[client-nav] mounted /client-nav/second')
	}, [])

	return (
		<div style={{ display: 'grid', gap: 12 }}>
			<h1 style={{ margin: 0 }}>Client-side navigation — second page</h1>
			<p>Console.log on mount is recorded by the browser SDK.</p>
			<nav style={{ display: 'flex', gap: 12 }}>
				<Link href="/client-nav">Back to /client-nav</Link>
			</nav>
		</div>
	)
}
