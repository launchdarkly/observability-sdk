import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
	EventType,
	IncrementalSource,
	type eventWithTime,
	type styleSheetRuleData,
} from '@highlight-run/rrweb-types'
import { record } from '@highlight-run/rrweb'
import { RecordSDK } from '../sdk/record'

// RecordSDK drains its buffer as it uploads, so keep our own copy of what rrweb
// emits.
const emitted: eventWithTime[] = []
vi.mock('@highlight-run/rrweb', async (importOriginal) => {
	const rrweb = await importOriginal<typeof import('@highlight-run/rrweb')>()
	const record = Object.assign(
		(options: NonNullable<Parameters<typeof rrweb.record>[0]>) =>
			rrweb.record({
				...options,
				emit: (event, isCheckout) => {
					emitted.push(event)
					options.emit?.(event, isCheckout)
				},
			}),
		rrweb.record,
	)
	return { ...rrweb, record }
})

vi.mock('../client/graph/generated/operations', () => ({
	getSdk: () => ({
		initializeSession: vi.fn().mockResolvedValue({
			initializeSession: {
				secure_id: 'test-session',
				project_id: '1',
			},
		}),
	}),
}))

vi.mock('../client/workers/highlight-client-worker?worker&inline', () => ({
	default: class MockWorker {
		onmessage: any
		postMessage() {}
	},
}))

// Rebuilds a sheet from the recorded rule events the way the replayer does:
// each event's adds before its removes, ignoring indices out of range.
const replayRules = (events: eventWithTime[], id: number) => {
	const replayed: string[] = []
	for (const event of events) {
		if (
			event.type !== EventType.IncrementalSnapshot ||
			event.data.source !== IncrementalSource.StyleSheetRule
		) {
			continue
		}
		const data = event.data as styleSheetRuleData
		if (data.id !== id) continue
		for (const { rule, index } of data.adds ?? []) {
			const at = typeof index === 'number' ? index : replayed.length
			if (at <= replayed.length) replayed.splice(at, 0, rule)
		}
		for (const { index } of data.removes ?? []) {
			const at = index as number
			if (at < replayed.length) replayed.splice(at, 1)
		}
	}
	return replayed
}

describe('RecordSDK stylesheet resync', () => {
	const pristineInsertRule = CSSStyleSheet.prototype.insertRule
	const pristineDeleteRule = CSSStyleSheet.prototype.deleteRule

	beforeEach(() => {
		emitted.length = 0
		vi.useFakeTimers()
	})

	afterEach(() => {
		vi.useRealTimers()
		CSSStyleSheet.prototype.insertRule = pristineInsertRule
		CSSStyleSheet.prototype.deleteRule = pristineDeleteRule
		document.head.innerHTML = ''
	})

	it('recovers a rule inserted mid-sheet while another script displaced the insertRule patch', async () => {
		const sdk = new RecordSDK({
			organizationID: '1',
			sessionSecureID: 'seed',
		})
		await sdk.start()

		const style = document.createElement('style')
		document.head.appendChild(style)
		await vi.advanceTimersByTimeAsync(0)
		const sheet = style.sheet as CSSStyleSheet
		// jsdom leaves ownerNode unset, which browsers never do; rrweb needs it to
		// tie CSSOM writes to the serialized <style> element.
		Object.defineProperty(sheet, 'ownerNode', { value: style })
		sheet.insertRule('.first { color: red; }', 0)
		sheet.insertRule('.second { color: green; }', 1)
		sheet.insertRule('.third { color: blue; }', 2)
		// Let a healthy resync tick record where the sheet has got to.
		await vi.advanceTimersByTimeAsync(sdk.styleSheetResyncInterval)

		// Another script tears down and reinstalls the insertRule it captured
		// before recording started, then a CSS-in-JS library inserts at a
		// component group's offset rather than appending.
		CSSStyleSheet.prototype.insertRule = pristineInsertRule
		sheet.insertRule('.missed { color: black; }', 1)
		await vi.advanceTimersByTimeAsync(sdk.styleSheetResyncInterval)

		const id = record.mirror.getId(style)
		// Recorded rules keep the text they were inserted with, so compare
		// selectors rather than serializations.
		const selector = (rule: string) =>
			rule.slice(0, rule.indexOf('{')).trim()
		const live = Array.from(sheet.cssRules, (rule) =>
			selector(rule.cssText),
		)
		expect(live).toEqual(['.first', '.missed', '.second', '.third'])
		expect(replayRules(emitted, id).map(selector)).toEqual(live)

		sdk.stop()
	})
})
