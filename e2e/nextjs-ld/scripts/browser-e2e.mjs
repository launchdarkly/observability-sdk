#!/usr/bin/env node
/* eslint-disable no-console */
/**
 * Browser end-to-end driver for e2e/nextjs-ld.
 *
 * Drives a real Chromium (playwright) session against the running example app:
 *  - waits for the observability SDK to initialize (sessionSecureID cookie)
 *  - clicks the client demo buttons (errors, traces, metrics, logs)
 *  - clicks the API-call buttons (session header attached to backend)
 *  - navigates SSR / ISR / streaming pages
 *  - records every telemetry response (/highlight-events, /v1/traces, ...)
 *  - prints the sessionSecureID so scripts can verify the backend spans
 *    exported to the otlp-capture receiver carry highlight.session_id
 *
 * Usage: PLAYWRIGHT_BROWSERS_PATH=~/.cache/ms-playwright node scripts/browser-e2e.mjs [--base http://127.0.0.1:3006]
 */
import { chromium } from 'playwright-core'

const args = process.argv.slice(2)
const base = args.includes('--base')
	? args[args.indexOf('--base') + 1]
	: 'http://127.0.0.1:3006'

const telemetryPaths = [
	'/highlight-events',
	'/v1/traces',
	'/v1/logs',
	'/v1/metrics',
]
const tally = new Map()
let telemetryResponses = []
let jsErrors = []
let consoleMessages = []

function isTelemetry(url) {
	return telemetryPaths.some((p) => url.pathname.endsWith(p))
}

const browser = await chromium.launch({
	headless: true,
	args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const context = await browser.newContext({
	viewport: { width: 1280, height: 900 },
})
const page = await context.newPage()

page.on('response', (res) => {
	const url = new URL(res.url())
	if (
		isTelemetry(url) &&
		url.pathname.startsWith(base ? new URL(base).pathname : '/')
	) {
		const key = url.pathname
		tally.set(
			key,
			(tally.get(key) ?? new Map()).set(
				res.status(),
				(tally.get(key)?.get(res.status()) ?? 0) + 1,
			),
		)
	}
})
page.on('console', (msg) => {
	const url = new URL(msg.location()?.url ?? 'about:blank')
	if (!isTelemetry(url))
		consoleMessages.push(`${msg.type()}: ${msg.text().slice(0, 120)}`)
})
page.on('pageerror', (err) => {
	consoleMessages.push(`pageerror: ${String(err).slice(0, 160)}`)
	jsErrors.push(String(err).slice(0, 160))
})

console.log(`> opening ${base}`)
await page.goto(base, { waitUntil: 'load', timeout: 30_000 })

// Wait for the SDK to initialize and expose the session cookie.
let sessionSecureID
const start = Date.now()
while (Date.now() - start < 30_000) {
	const cookies = await context.cookies(base)
	sessionSecureID = cookies.find((c) => c.name === 'sessionSecureID')?.value
	if (sessionSecureID) break
	await page.waitForTimeout(250)
}
if (!sessionSecureID) {
	console.error('✗ SDK never initialized (no sessionSecureID cookie)')
	await browser.close()
	process.exit(1)
}
console.log(`✓ session initialized: ${sessionSecureID}`)

// Give the initial batch (load events + /highlight-events) a moment.
await page.waitForTimeout(2500)

// --- click through the demo buttons -------------------------------------
async function clickButton(label) {
	const btn = page.locator('button', { hasText: label }).first()
	try {
		await btn.click({ timeout: 5000 })
		console.log(`✓ clicked: ${label}`)
	} catch (e) {
		console.error(
			`✗ could not click "${label}": ${e.message.split('\n')[0]}`,
		)
	}
}

await clickButton('LDObserve.recordError')
await page.waitForTimeout(500)
await clickButton('console.error')
await page.waitForTimeout(500)
await clickButton('startManualSpan')
await page.waitForTimeout(500)
await clickButton('recordCount + recordHistogram')
await page.waitForTimeout(500)
await clickButton('GET /api/test?success=true')
await page.waitForTimeout(1500)
await clickButton('GET /api/external')
await page.waitForTimeout(2500)

// event-handler throw + boundary crash (fresh page so the boundary state resets)
await clickButton('throw in event handler (uncaught)')
await page.waitForTimeout(600)
await clickButton('crash a component (ErrorBoundary)')
await page.waitForTimeout(600)
await clickButton('Reset')
await page.waitForTimeout(600)

// --- navigate the server pages ------------------------------------------
for (const path of [
	'/ssr',
	'/isr',
	'/streaming',
	'/dynamic/42',
	'/legacy',
	'/',
]) {
	await page.goto(base + path, { waitUntil: 'load', timeout: 30_000 })
	await page.waitForTimeout(1200)
	console.log(`✓ visited ${path}`)
}

// Wait for the client-side batch exporters to flush.
await page.waitForTimeout(6000)

// Let the server-side BatchSpanProcessor flush its final batches.
await new Promise((r) => setTimeout(r, 6000))

console.log('\n=== telemetry responses (page → same-origin proxy → LD) ===')
for (const [path, statuses] of tally) {
	for (const [status, count] of statuses) {
		console.log(`  ${path} → ${status} ×${count}`)
	}
}
if (tally.size === 0) {
	console.log('  (no telemetry requests observed!)')
}

console.log(
	`\n=== browser console errors captured === ${jsErrors.length} pageerror(s)`,
)
for (const e of jsErrors.slice(0, 5)) console.log('  ', e)

console.log('\nSESSION_SECURE_ID=' + sessionSecureID)
await browser.close()
