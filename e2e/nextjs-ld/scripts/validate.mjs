#!/usr/bin/env node
/**
 * End-to-end validation driver for e2e/nextjs-ld.
 *
 * Verifies (from the CLI, without a browser):
 *   1. every demo route responds with the expected status
 *   2. telemetry actually reaches LaunchDarkly:
 *      a. the same-origin proxy rewrites forward to LaunchDarkly
 *         (POST /v1/traces with an empty protobuf returns the ingest
 *         endpoint's status rather than a Next 404)
 *      b. the direct staging endpoints are reachable & accept OTLP pushes
 *
 * Usage:
 *   node scripts/validate.mjs [--base http://127.0.0.1:3006]
 *
 * The browser-side checks (session replay recording & playback, error list,
 * trace waterfall shapes) are documented in README.md.
 */
const args = process.argv.slice(2)
const baseIdx = args.indexOf('--base')
const BASE = baseIdx >= 0 ? args[baseIdx + 1] : 'http://127.0.0.1:3006'

const fail = []
let total = 0

async function check(name, fn) {
	total++
	try {
		const ok = await fn()
		if (ok) {
			console.log(`  ✓ ${name}`)
			return
		}
		throw new Error('check failed')
	} catch (e) {
		fail.push({ name, error: e })
		console.error(`  ✗ ${name}: ${e.message ?? e}`)
	}
}

async function statusOf(path, init = {}) {
	const res = await fetch(new URL(path, BASE), {
		...init,
		redirect: 'manual',
	})
	// drain body
	await res.text().catch(() => {})
	return res.status
}

async function waitReady(timeoutMs = 60_000) {
	const start = Date.now()
	for (;;) {
		try {
			const res = await fetch(new URL('/', BASE))
			await res.text()
			if (res.status === 200) return
		} catch {
			/* not up yet */
		}
		if (Date.now() - start > timeoutMs) {
			throw new Error(
				`app at ${BASE} did not become ready in ${timeoutMs}ms`,
			)
		}
		await new Promise((r) => setTimeout(r, 1000))
	}
}

console.log(`\n=== 1. Route checks against ${BASE} ===\n`)

await waitReady()

const routes = [
	['/', 200],
	['/ssr', 200],
	['/isr', 200],
	['/ssg', 200],
	['/streaming', 200],
	['/server-action', 200],
	['/client-nav', 200],
	['/client-nav/second', 200],
	['/dynamic/42', 200],
	['/legacy', 200],
	// errors:
	['/danger', 500],
	['/legacy/oops', 500],
	['/does-not-exist', 404],
]
for (const [path, expected] of routes) {
	await check(`GET ${path} -> ${expected}`, async () => {
		const status = await statusOf(path)
		return status === expected
	})
}

const apis = [
	['/api/test?success=true', 'GET', 200],
	['/api/test?success=false', 'GET', 500],
	['/api/slow?ms=50', 'GET', 200],
	['/api/external', 'GET', 200],
	['/api/legacy-success', 'GET', 200],
	['/api/legacy-error', 'GET', 500],
]
for (const [path, method, expected] of apis) {
	await check(`${method} ${path} -> ${expected}`, async () => {
		const status = await statusOf(path, { method })
		return status === expected
	})
}

await check('POST /api/echo -> 200 + echoes body', async () => {
	const res = await fetch(new URL('/api/echo', BASE), {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify({ hello: 'world' }),
	})
	const json = await res.json().catch(() => null)
	return res.status === 200 && json?.echo?.hello === 'world'
})

console.log(`\n=== 2. Telemetry proxy checks ===\n`)

// An empty (0-byte) OTLP protobuf POST should be forwarded by the proxy
// rewrites to the LaunchDarkly OTLP endpoint. If the rewrites are not
// configured, Next answers 404 and the check fails.
await check(
	'POST /v1/traces is proxied to LaunchDarkly (not a 404)',
	async () => {
		const res = await fetch(new URL('/v1/traces', BASE), {
			method: 'POST',
			headers: { 'content-type': 'application/x-protobuf' },
			body: '',
		})
		await res.text().catch(() => {})
		return res.status !== 404
	},
)

await check(
	'POST /highlight-events is proxied to LaunchDarkly (not a 404)',
	async () => {
		const res = await fetch(new URL('/highlight-events', BASE), {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify([]),
		})
		await res.text().catch(() => {})
		return res.status !== 404
	},
)

console.log(`\n=== 3. Direct endpoint reachability ===\n`)

// Reachability of the configured LaunchDarkly targets (staging by default).
const LD_ENV = process.env.LAUNCHDARKLY_ENV ?? 'staging'
const BACKEND =
	process.env.LAUNCHDARKLY_BACKEND_URL ??
	(LD_ENV === 'staging'
		? 'https://pub.observability.ld-stg.launchdarkly.com'
		: 'https://pub.observability.app.launchdarkly.com')
const OTEL =
	process.env.LAUNCHDARKLY_OTEL_ENDPOINT ??
	(LD_ENV === 'staging'
		? 'https://otel.observability.ld-stg.launchdarkly.com:4318'
		: 'https://otel.observability.app.launchdarkly.com')

await check(`GET ${BACKEND} is reachable`, async () => {
	const res = await fetch(BACKEND).catch((e) => {
		throw new Error(`fetch failed: ${e.cause?.code ?? e.message}`)
	})
	await res.text().catch(() => {})
	return true
})

await check(`POST ${OTEL}/v1/traces reachable`, async () => {
	const res = await fetch(`${OTEL}/v1/traces`, {
		method: 'POST',
		headers: { 'content-type': 'application/x-protobuf' },
		body: '',
	}).catch((e) => {
		throw new Error(`fetch failed: ${e.cause?.code ?? e.message}`)
	})
	await res.text().catch(() => {})
	return true
})

console.log(`
=== Summary ===`)
console.log(`${total} checks, ${fail.length} failed`)
if (fail.length) {
	console.log('\nFailures:')
	for (const f of fail)
		console.log(`  ✗ ${f.name}: ${f.error?.message ?? f.error}`)
	process.exit(1)
}
console.log('All CLI validations passed.')
console.log(`
Next: manual UI verification (see README.md):
  - open ${BASE} in a browser, click through the demo buttons
  - session replay, errors, logs: check the LaunchDarkly UI
  - traces: confirm backend spans (SSR/ISR/API) carry the session correlation`)
