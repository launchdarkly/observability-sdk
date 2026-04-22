import { LDObserve } from '@launchdarkly/observability'
import { useEffect, useState } from 'react'

// Manual-test scaffolding for the "flush OTEL spans on page unload" fix.
// Open DevTools → Network and filter for `/v1/traces`. With the fix in place,
// every button below should produce a POST that shows up as `Type: fetch`
// with the request staying in flight (keepalive) through the navigation /
// visibility change. Before the fix, the batched spans sat in memory for up
// to 30s and the XHR transport was cancelled by the browser on unload, so the
// request never landed server-side.
const UNLOAD_LOG_KEY = 'flush-on-unload.events'

type UnloadEvent = { ts: number; name: string; detail?: string }

function readUnloadLog(): UnloadEvent[] {
	try {
		return JSON.parse(sessionStorage.getItem(UNLOAD_LOG_KEY) ?? '[]')
	} catch {
		return []
	}
}

function recordUnloadEvent(name: string, detail?: string) {
	const log = readUnloadLog()
	log.push({ ts: Date.now(), name, detail })
	try {
		sessionStorage.setItem(UNLOAD_LOG_KEY, JSON.stringify(log))
	} catch {
		// ignore
	}
}

export default function FlushOnUnload() {
	const [lastSpan, setLastSpan] = useState<string>('')
	const [unloadLog, setUnloadLog] = useState<UnloadEvent[]>(() =>
		readUnloadLog(),
	)

	// Persist unload events to sessionStorage so we can observe them after
	// navigating back from example.com — console.log from pagehide/unload is
	// unreliable across a cross-document nav.
	useEffect(() => {
		const onPageHide = (e: PageTransitionEvent) =>
			recordUnloadEvent('pagehide', `persisted=${e.persisted}`)
		const onVisibility = () =>
			recordUnloadEvent('visibilitychange', document.visibilityState)
		const onBeforeUnload = () => recordUnloadEvent('beforeunload')
		const onUnload = () => recordUnloadEvent('unload')
		const onFreeze = () => recordUnloadEvent('freeze')
		window.addEventListener('pagehide', onPageHide)
		document.addEventListener('visibilitychange', onVisibility)
		window.addEventListener('beforeunload', onBeforeUnload)
		window.addEventListener('unload', onUnload)
		document.addEventListener('freeze', onFreeze)
		return () => {
			window.removeEventListener('pagehide', onPageHide)
			document.removeEventListener('visibilitychange', onVisibility)
			window.removeEventListener('beforeunload', onBeforeUnload)
			window.removeEventListener('unload', onUnload)
			document.removeEventListener('freeze', onFreeze)
		}
	}, [])

	const emitSpan = (name: string) => {
		const suffix = Math.random().toString(36).slice(2, 8)
		const spanName = `${name}-${suffix}`
		LDObserve.startSpan(spanName, (span) => {
			span?.setAttribute('flush-on-unload.test', true)
			span?.setAttribute('flush-on-unload.scenario', name)
		})
		setLastSpan(spanName)
		return spanName
	}

	return (
		<div style={{ padding: 16, maxWidth: 720 }}>
			<h2>Flush on unload</h2>
			<p>
				Each button ends a span and then immediately triggers the unload
				path the fix targets. Watch DevTools → Network, filter{' '}
				<code>v1/traces</code>, and verify the POST goes out before the
				navigation completes.
			</p>
			<p>
				Last span emitted: <code>{lastSpan || '(none)'}</code>
			</p>

			<details open style={{ marginBottom: 16 }}>
				<summary>
					<strong>Unload events from previous navigation</strong> (
					{unloadLog.length})
					<button
						onClick={() => {
							sessionStorage.removeItem(UNLOAD_LOG_KEY)
							setUnloadLog([])
						}}
						style={{ marginLeft: 12 }}
					>
						Clear
					</button>
				</summary>
				<pre
					style={{
						background: '#f6f6f6',
						padding: 8,
						fontSize: 12,
						maxHeight: 200,
						overflow: 'auto',
					}}
				>
					{unloadLog.length === 0
						? '(none — navigate away and come back to see events)'
						: unloadLog
								.map(
									(e) =>
										`+${e.ts - unloadLog[0].ts}ms  ${e.name}${e.detail ? ` (${e.detail})` : ''}`,
								)
								.join('\n')}
				</pre>
			</details>

			<div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
				<button
					onClick={() => {
						emitSpan('hard-nav')
						// Full-page navigation — fires pagehide and cancels XHR.
						window.location.href = '/welcome'
					}}
				>
					Span + hard navigate to /welcome
				</button>

				<button
					onClick={() => {
						emitSpan('hard-nav-external')
						window.location.href = 'https://example.com/'
					}}
				>
					Span + navigate to example.com (cross-origin unload)
				</button>

				<button
					onClick={() => {
						emitSpan('spa-nav')
						// SPA nav — does NOT fire pagehide, but the 5s batch
						// delay should still catch it. Use "Span (no nav)" to
						// compare the before/after scheduledDelayMillis change.
						window.history.pushState({}, '', '/welcome')
						window.dispatchEvent(new PopStateEvent('popstate'))
					}}
				>
					Span + SPA navigate (pushState, no unload)
				</button>

				<button
					onClick={() => {
						emitSpan('visibility-hidden')
						// Simulate tab hidden without actually navigating.
						// Flush path is triggered by the visibilitychange
						// listener registered in registerFlushOnUnload().
						Object.defineProperty(document, 'visibilityState', {
							configurable: true,
							get: () => 'hidden',
						})
						document.dispatchEvent(new Event('visibilitychange'))
						// Flip it back so the page remains usable.
						setTimeout(() => {
							Object.defineProperty(document, 'visibilityState', {
								configurable: true,
								get: () => 'visible',
							})
							document.dispatchEvent(
								new Event('visibilitychange'),
							)
						}, 500)
					}}
				>
					Span + dispatch visibilitychange=hidden
				</button>

				<button
					onClick={() => {
						emitSpan('pagehide')
						window.dispatchEvent(
							new PageTransitionEvent('pagehide'),
						)
					}}
				>
					Span + dispatch pagehide (no nav)
				</button>

				<button
					onClick={() => {
						emitSpan('reload')
						window.location.reload()
					}}
				>
					Span + reload()
				</button>

				<button
					onClick={() => {
						emitSpan('batch')
					}}
				>
					Span (no nav — should flush on 5s batch timer)
				</button>

				<button
					onClick={() => {
						for (let i = 0; i < 50; i++) {
							emitSpan('burst')
						}
						window.location.href = '/welcome'
					}}
				>
					50 spans + hard navigate (batch size sanity check)
				</button>
			</div>
		</div>
	)
}
