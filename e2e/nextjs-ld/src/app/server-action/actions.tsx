'use server'

import { LDObserve } from '@launchdarkly/observability-next/server'

/**
 * Next.js server actions run as POST requests to the page route, so the http
 * instrumentation records them as spans. Inside the action you can also record
 * explicit errors/metrics visible in LaunchDarkly.
 */
export async function createOrder(formData: FormData) {
	const item = String(formData.get('item') ?? '')
	console.info('[server-action] createOrder called', { item })

	LDObserve.recordCount({
		name: 'demo.orders.created',
		value: 1,
		tags: [{ name: 'item', value: item }],
	})

	// Simulate some latency so the action span is measurable.
	await new Promise((r) => setTimeout(r, 250))

	return { ok: true, item, at: new Date().toISOString() }
}

export async function failOrder(formData: FormData) {
	const item = String(formData.get('item') ?? '')
	console.warn('[server-action] failOrder called', { item })

	LDObserve.recordCount({
		name: 'demo.orders.failed',
		value: 1,
		tags: [{ name: 'item', value: item }],
	})

	await new Promise((r) => setTimeout(r, 250))

	throw new Error(`Server action failed on purpose (item=${item})`)
}
