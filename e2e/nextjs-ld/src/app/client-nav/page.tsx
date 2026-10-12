'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'

export default function ClientNavPage() {
	const pathname = usePathname()

	return (
		<div style={{ display: 'grid', gap: 12 }}>
			<h1 style={{ margin: 0 }}>Client-side navigation</h1>
			<p>
				Current route: <code>{pathname}</code>. Follow the link below
				and back twice; the session replay records both SPA transitions
				and each navigation logs a client console entry.
			</p>
			<nav style={{ display: 'flex', gap: 12 }}>
				<Link href="/client-nav/second">Go to /client-nav/second</Link>
				{pathname !== '/client-nav' && (
					<Link href="/client-nav">Go back to /client-nav</Link>
				)}
			</nav>
			<p style={{ color: '#64748b', fontSize: 14 }}>
				flash from: {pathname}
			</p>
		</div>
	)
}
